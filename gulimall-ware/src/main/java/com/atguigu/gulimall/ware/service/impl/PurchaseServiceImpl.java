package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.common.constant.WareConstant;
import com.atguigu.gulimall.ware.entity.PurchaseDetailEntity;
import com.atguigu.gulimall.ware.service.PurchaseDetailService;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.MergeVo;
import com.atguigu.gulimall.ware.vo.PurchaseDoneVo;
import com.atguigu.gulimall.ware.vo.PurchaseItemDoneVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.PurchaseDao;
import com.atguigu.gulimall.ware.entity.PurchaseEntity;
import com.atguigu.gulimall.ware.service.PurchaseService;
import org.springframework.transaction.annotation.Transactional;


@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {

    @Autowired
    PurchaseDetailService detailService;

    @Autowired
    WareSkuService wareSkuService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {

        QueryWrapper<PurchaseEntity> wrapper = new QueryWrapper<>();
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params), wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPageUnreceive(Map<String, Object> params) {

        QueryWrapper<PurchaseEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 0).or().eq("status", 1);

        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params), wrapper
        );

        return new PageUtils(page);
    }

    @Transient
    @Override
    public void mergePurchase(MergeVo mergeVo) {
        Long purchaseId = mergeVo.getPurchaseId();
        if (purchaseId == null) {
            //??? ?????????
            PurchaseEntity purchaseEntity = new PurchaseEntity();
            purchaseEntity.setStatus(WareConstant.purchaseStatusEnum.CREATED.getCode());
            purchaseEntity.setCreateTime(new Date());
            purchaseEntity.setUpdateTime(new Date());
            this.save(purchaseEntity);
            purchaseId = purchaseEntity.getId();
        }
        //TODO ???????????????????????? 0 / 1???????????????
        //??? ?????????
        List<Long> items = mergeVo.getItems();
        Long finalPurchaseId = purchaseId;
        List<PurchaseDetailEntity> collect = items.stream().map((item) -> {
            PurchaseDetailEntity purchaseDetailEntity = new PurchaseDetailEntity();
            purchaseDetailEntity.setId(item); //[1,3,6]
            purchaseDetailEntity.setPurchaseId(finalPurchaseId);// 2
            purchaseDetailEntity.setStatus(WareConstant.purchaseDetailStatusEnum.ASSIGNED.getCode());
            return purchaseDetailEntity;
        }).collect(Collectors.toList());

        detailService.updateBatchById(collect);// ??????????????????
        PurchaseEntity purchaseEntity = new PurchaseEntity();
        purchaseEntity.setId(purchaseId);
        purchaseEntity.setUpdateTime(new Date());
        this.updateById(purchaseEntity);// ???????????????
    }

    @Override
    public void received(List<Long> ids) {
        //1 ?????????????????????????????? / ?????????
        List<PurchaseEntity> collect = ids.stream().map((id) -> {
            PurchaseEntity purchaseEntity = this.getById(id);
            return purchaseEntity;
        }).filter((id) -> {
            if (id.getStatus() == WareConstant.purchaseStatusEnum.CREATED.getCode() ||
                    id.getStatus() == WareConstant.purchaseStatusEnum.ASSIGNED.getCode()) {
                return true;
            }
            return false;
        }).map((purchaseEntity)->{
            purchaseEntity.setStatus(WareConstant.purchaseStatusEnum.RECEIVE.getCode());
            purchaseEntity.setUpdateTime(new Date());
            return purchaseEntity;
        }).collect(Collectors.toList());

        //2 ????????????????????????
        this.updateBatchById(collect);

        //3 ????????????????????????
        collect.forEach((purchaseEntity)->{
            List<PurchaseDetailEntity> purchaseDetailEntities = detailService.listDetailByPurchaseId(purchaseEntity.getId());
            List<PurchaseDetailEntity> collect1 = purchaseDetailEntities.stream().map((purchaseDetailEntity) -> {
                PurchaseDetailEntity purchaseDetailEntity1 = new PurchaseDetailEntity();
                purchaseDetailEntity1.setId(purchaseDetailEntity.getId());
                purchaseDetailEntity1.setStatus(WareConstant.purchaseDetailStatusEnum.BUYING.getCode());
                return purchaseDetailEntity1;
            }).collect(Collectors.toList());
            detailService.updateBatchById(collect1);
        });
    }

    @Transactional
    @Override
    public void done(PurchaseDoneVo doneVo) {

        Long id = doneVo.getId();
        //2???????????????????????????
        Boolean flag = true;
        List<PurchaseItemDoneVo> items = doneVo.getItems();

        List<PurchaseDetailEntity> updates = new ArrayList<>();
//        if (items != null) {
            for (PurchaseItemDoneVo item: items) {
                if (item != null) {
                    PurchaseDetailEntity detailEntity = new PurchaseDetailEntity();
                    if (item.getStatus() == WareConstant.purchaseDetailStatusEnum.HASERROR.getCode()) {
                        flag = false;
                        detailEntity.setStatus(item.getStatus());
                    } else {
                        detailEntity.setStatus(WareConstant.purchaseDetailStatusEnum.FINISH.getCode());
                        //3?????????????????????????????????
                        PurchaseDetailEntity entity = detailService.getById(item.getItemId());
                        wareSkuService.addStock(entity.getSkuId(), entity.getWareId(), entity.getSkuNum());

                    }
                    detailEntity.setId(item.getItemId());
                    updates.add(detailEntity);
                }
            }
//        }

        if (updates != null && updates.size() >0)
            detailService.updateBatchById(updates);

        //1????????????????????????
        PurchaseEntity purchaseEntity = new PurchaseEntity();
        purchaseEntity.setId(id);
        purchaseEntity.setStatus(flag?WareConstant.purchaseStatusEnum.FINISH.getCode():WareConstant.purchaseStatusEnum.HASERROR.getCode());
        purchaseEntity.setUpdateTime(new Date());
        this.updateById(purchaseEntity);
    }
}