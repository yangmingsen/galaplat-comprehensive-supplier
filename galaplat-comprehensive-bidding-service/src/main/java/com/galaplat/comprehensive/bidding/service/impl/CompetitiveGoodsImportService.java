package com.galaplat.comprehensive.bidding.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.galaplat.base.core.common.exception.BaseException;
import com.galaplat.base.core.common.utils.JsonUtils;
import com.galaplat.baseplatform.permissions.feign.IFeignPermissions;
import com.galaplat.comprehensive.bidding.dao.IJbxtActivityDao;
import com.galaplat.comprehensive.bidding.dao.IJbxtGoodsDao;
import com.galaplat.comprehensive.bidding.dao.dos.JbxtActivityDO;
import com.galaplat.comprehensive.bidding.dao.params.JbxtActivityParam;
import com.galaplat.comprehensive.bidding.dao.params.JbxtGoodsParam;
import com.galaplat.comprehensive.bidding.dao.params.validate.InsertParam;
import com.galaplat.comprehensive.bidding.enums.ActivityStatusEnum;
import com.galaplat.comprehensive.bidding.param.JbxtGoodsExcelParam;
import com.galaplat.comprehensive.bidding.utils.BeanValidateUtils;
import com.galaplat.comprehensive.bidding.vos.ValidateResultVO;
import com.galaplat.platformdocking.base.core.utils.CopyUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.galaplat.baseplatform.file.upload.service.IImportSubMethodWithParamService;
import org.galaplat.baseplatform.file.upload.vos.ImportVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**@
 * @Description: 竞标活动竞品导入
 * @Author: weiyuxuan
 * @CreateDate: 2020/7/9 20:15
 */
@Service("competitiveGoodsExcelService")
public class CompetitiveGoodsImportService implements IImportSubMethodWithParamService<JbxtGoodsExcelParam> {

    private static final Logger log = LoggerFactory.getLogger(CompetitiveGoodsImportService.class);

    @Autowired
    private IFeignPermissions feignPermissions;

    @Autowired
    private IJbxtGoodsDao goodsDao;

    @Autowired
    private IJbxtActivityDao  activityDao;


    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED,rollbackFor = Exception.class)
    public List<JbxtGoodsExcelParam> insertExcelDate(List<Map<String, Object>> list, ImportVO importVO) {
        if (CollectionUtils.isEmpty(list)) {return Collections.emptyList();}
        List<JbxtGoodsExcelParam>  errorList = Lists.newArrayList();
        List<JbxtGoodsParam>  saveList = Lists.newArrayList();

        String creatorName = getCreatorName(importVO.getCreator());
        String paramJson = importVO.getParamJson();
        String activityCode= null;

        if (StringUtils.isNotEmpty(paramJson)) {
            Map<String, Object> mapVO  = JSONObject.parseObject(paramJson, new TypeReference<Map<String, Object>>(){});
            activityCode = (String) mapVO.get("bidActivityCode");
        }

        for (Map<String, Object>  goodsMap: list) {
            JbxtGoodsExcelParam goodsExcelParam = null;
            try {
                goodsExcelParam = JsonUtils.toObject(JsonUtils.toJson(goodsMap), JbxtGoodsExcelParam.class);
            } catch (Exception e) {
                log.error(" 竞品导入格式化异常【{}】,【{}】",e.getMessage(), e );
            }
            ValidateResultVO resultVO =  BeanValidateUtils.validateEntity(goodsExcelParam, InsertParam.class);
            if (resultVO.isHasErrors() && null != goodsExcelParam) {
                goodsExcelParam.setErrorMsg(resultVO.getErrorMessage());
                errorList.add(goodsExcelParam);
            } else {
                if (null != goodsExcelParam) {
                    goodsExcelParam.setActivityCode(activityCode);
                    goodsExcelParam.setCreatedTime(new Date());
                    goodsExcelParam.setUpdatedTime(new Date());
                    goodsExcelParam.setCreator(creatorName);
                    goodsExcelParam.setStatus("0");
                    JbxtGoodsParam goodsParam =  new JbxtGoodsParam();
                    CopyUtil.copyPropertiesExceptEmpty(goodsExcelParam, goodsParam);
                    saveList.add(goodsParam);
                }
            }
        }
        if (CollectionUtils.isNotEmpty(saveList)) {
            goodsDao.batchInsertOrUpdate(saveList);
            JbxtActivityDO activityDO = activityDao.getJbxtActivity(JbxtActivityParam.builder().code(activityCode).build());
            if (null != activityDO && activityDO.getStatus().equals(ActivityStatusEnum.UNEXPORT.getCode())) {
                activityDao.updateBidActivity(JbxtActivityDO.builder().code(activityCode).status(ActivityStatusEnum.EXPORT_NO_SATRT.getCode()).build());
            }
        }

        return errorList;
    }

    @Override
    public Object getExcelObject() {
        return new JbxtGoodsExcelParam();
    }

    @Override
    public boolean validateExcelRow(Row row, int i) throws BaseException {
        return false;
    }

    private String getCreatorName(String creator) {
        String creatorName = null;
        String result = "result";
                //获取用户名
                try {
                    JsonNode userByCode = feignPermissions.getUserByCode(creator);
                    if (userByCode == null || userByCode.get(result) == null) {
                        throw  new BaseException("创建者信息获取异常！","创建者信息获取异常！");
                    }
                    if (userByCode.get(result).get("name") != null) {
                        creatorName = userByCode.get(result).get("name").asText();
                    }else{
                        creatorName = "没有查询到用户名称";
                    }

                } catch (BaseException e) {
                    log.error(e.getMessage(),e);
                }
                return creatorName;
    }
}
