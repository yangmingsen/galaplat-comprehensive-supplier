package com.galaplat.comprehensive.bidding.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.galaplat.base.core.common.exception.BaseException;
import com.galaplat.base.core.common.utils.JsonUtils;
import com.galaplat.base.core.common.utils.RegexUtils;
import com.galaplat.baseplatform.permissions.feign.IFeignPermissions;
import com.galaplat.comprehensive.bidding.dao.ActivityDao;
import com.galaplat.comprehensive.bidding.dao.UserDao;
import com.galaplat.comprehensive.bidding.dao.dos.ActivityDO;
import com.galaplat.comprehensive.bidding.dao.dos.UserDO;
import com.galaplat.comprehensive.bidding.dao.params.JbxtActivityParam;
import com.galaplat.comprehensive.bidding.dao.params.JbxtUserParam;
import com.galaplat.comprehensive.bidding.enums.ActivityStatusEnum;
import com.galaplat.comprehensive.bidding.enums.CodeNameEnum;
import com.galaplat.comprehensive.bidding.param.JbxtSupplierExcelParam;
import com.galaplat.comprehensive.bidding.service.ICompetitiveListManageService;
import com.galaplat.comprehensive.bidding.utils.IdWorker;
import com.galaplat.comprehensive.bidding.utils.ImportExcelValidateMapUtil;
import com.galaplat.comprehensive.bidding.utils.Tuple;
import com.galaplat.platformdocking.base.core.utils.CopyUtil;
import com.google.common.collect.Maps;
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

import java.util.*;

/**
 * @
 * @Description: 竞标活动竞品导入
 * @Author: weiyuxuan
 * @CreateDate: 2020/7/9 20:15
 */
@Service("competitiveSupplierImportService")
public class CompetitiveSupplierImportService implements IImportSubMethodWithParamService<JbxtSupplierExcelParam> {

    private static final Logger log = LoggerFactory.getLogger(CompetitiveSupplierImportService.class);

    /* 当前导入供应商已使用的代号 */
    private  Map<String, String> addingCodeNameMap = Maps.newConcurrentMap();

    /* 当前导入供应商已使用的号码 */
    private  Map<String, String> addingPhoneMap = Maps.newConcurrentMap();

    /* 当前导入供应商已使用的邮箱 */
    private  Map<String, String> addingEmailMap = Maps.newConcurrentMap();

    @Autowired
    private IdWorker worker;

    @Autowired
    private IFeignPermissions feignPermissions;

    @Autowired
    private UserDao userDao;

    @Autowired
    private ActivityDao activityDao;

    @Autowired
    private ICompetitiveListManageService manageService;


    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public List<JbxtSupplierExcelParam> insertExcelDate(List<Map<String, Object>> list, ImportVO importVO) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 清除map里的值
        addingCodeNameMap.clear();
        addingEmailMap.clear();
        addingPhoneMap.clear();

        List<JbxtSupplierExcelParam> errorList = Lists.newArrayList();
        List<JbxtUserParam> saveList = Lists.newArrayList();
        List<JbxtSupplierExcelParam> rightList = Lists.newArrayList();

        String creatorName = getCreatorName(importVO.getCreator());
        String paramJson = importVO.getParamJson();
        String activityCode = null;

        if (StringUtils.isNotEmpty(paramJson)) {
            Map<String, Object> mapVO = JSON.parseObject(paramJson, new TypeReference<Map<String, Object>>() {
            });
            activityCode = (String) mapVO.get("bidActivityCode");
        }
        ActivityDO activityDO = activityDao.getJbxtActivity(JbxtActivityParam.builder().code(activityCode).build());
        // 如果竞标活动的状态是结束时，不允许导入
        if (activityDO.getStatus().equals(ActivityStatusEnum.FINISH.getCode())) {
            return getErrorSupplierExcelParams(list, "竞标活动已结束，不允许导入供应商信息！");
        }

        //超过20个导入失败
        if ((CollectionUtils.isNotEmpty(list) && list.size() > 20) || CollectionUtils.isEmpty(list)) {
            return getErrorSupplierExcelParams(list, "供应商个数不允许超过20个！");
        }

        if (null != activityDO && CollectionUtils.isNotEmpty(list)) {
            for (Map<String, Object> userMap : list) {
                SupplierExcelParamValidateResult excelParamValidateResult = new SupplierExcelParamValidateResult(userMap).invoke();
                JbxtSupplierExcelParam supplierExcelParam = excelParamValidateResult.getSupplierExcelParam();
                StringBuilder errorMsg = excelParamValidateResult.getErrorMsg();

                if (StringUtils.isNotEmpty(errorMsg.toString())) {
                    String serialNuimber = (String)userMap.get("serialNumber");
                    if (StringUtils.isNotBlank(serialNuimber)) {
                        supplierExcelParam.setSerialNumber(Integer.parseInt(serialNuimber));
                    }
                    supplierExcelParam.setErrorMsg(errorMsg.toString());
                    errorList.add(supplierExcelParam);
                } else {
                    try {
                        supplierExcelParam = JsonUtils.toObject(JsonUtils.toJson(userMap), JbxtSupplierExcelParam.class);
                    } catch (Exception e) {
                        log.error(" 竞品导入格式化异常【{}】,【{}】", e.getMessage(), e);
                    }
                    supplierExcelParam.setActivityCode(activityCode);
                    supplierExcelParam.setCreatedTime(new Date());
                    supplierExcelParam.setUpdatedTime(new Date());
                    supplierExcelParam.setCreator(creatorName);
                    JbxtUserParam userParam = new JbxtUserParam();
                    CopyUtil.copyPropertiesExceptEmpty(supplierExcelParam, userParam);
                    rightList.add(supplierExcelParam);
                    saveList.add(userParam);
                }
            }

            if (CollectionUtils.isNotEmpty(saveList)) {
                List<UserDO> oldSupplierList = userDao.listJbxtUser(JbxtUserParam.builder().activityCode(activityCode).build());
                List<JbxtUserParam> addSuppliers = getAddSupplierList(oldSupplierList, saveList, creatorName, activityCode);
                List<JbxtUserParam> updateSuppliers = getUpdateSupplierList(oldSupplierList, saveList, creatorName, activityCode);
                List<String> deleteCodes = getDeleteSupplierList(oldSupplierList, saveList);

                if (!ActivityStatusEnum.BIDING.getCode().equals(activityDO.getStatus())
                        && !ActivityStatusEnum.FINISH.getCode().equals(activityDO.getStatus())) {
                    addSuppliers.addAll(updateSuppliers);
                    if (CollectionUtils.isNotEmpty(addSuppliers)) {
                        userDao.btachInsertAndUpdate(addSuppliers);
                    }
                    if (CollectionUtils.isNotEmpty(deleteCodes)) {
                        userDao.batchDeleteUser(deleteCodes, activityCode);
                    }

                } else if (ActivityStatusEnum.BIDING.getCode().equals(activityDO.getStatus())) {
                    if (CollectionUtils.isNotEmpty(addSuppliers)) {
                        userDao.btachInsertAndUpdate(addSuppliers);
                    }
                }
                if (manageService.checkActivityInfoComplete(activityCode)) {
                    activityDao.updateBidActivity(ActivityDO.builder().code(activityCode).status(ActivityStatusEnum.IMPORT_NO_SATRT.getCode()).build());
                }

                // 更新竞标活动供应商个数
                List<UserDO> supplierList = userDao.listJbxtUser(JbxtUserParam.builder().activityCode(activityCode).build());
                activityDao.updateByPrimaryKeySelective(ActivityDO.builder().code(activityCode).supplierNum(null == supplierList ? 0 : supplierList.size()).build());
            }

            if (CollectionUtils.isNotEmpty(errorList) && CollectionUtils.isNotEmpty(rightList)) {
                errorList.addAll(rightList);
            }

        }// if
        return errorList;
    }

    @Override
    public Object getExcelObject() {
        return new JbxtSupplierExcelParam();
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
                throw new BaseException("创建者信息获取异常！", "创建者信息获取异常！");
            }
            if (userByCode.get(result).get("name") != null) {
                creatorName = userByCode.get(result).get("name").asText();
            } else {
                creatorName = creator;
            }

        } catch (BaseException e) {
            log.error(e.getMessage(), e);
        }
        return creatorName;
    }

    /***
     * 校验手机号
     * @param excelParam
     * @return
     */
    private String validateExcelParam(JbxtSupplierExcelParam excelParam) {
        StringBuilder error = new StringBuilder("");
        String phone = excelParam.getPhone();
        String emailAddress = excelParam.getEmailAddress();

        if (StringUtils.isNotBlank(phone)) {
            if (!RegexUtils.isMobile(phone)) {
                error.append("手机号格式格式错误！");
            }else {
                if (!addingPhoneMap.containsKey(phone)) {
                    addingPhoneMap.put(phone, phone);
                } else {
                    error.append("手机号" + phone + "重复！");
                }
            }
        }

        if (StringUtils.isNotBlank(emailAddress)) {
            if (!RegexUtils.isEmail(emailAddress)) {
                error.append("邮箱地址格式错误！");
            } else {
                if (!addingEmailMap.containsKey(emailAddress)) {
                    addingEmailMap.put(emailAddress, emailAddress);
                } else {
                    error.append("邮箱地址" + emailAddress + "重复！");
                }
            }
        }

        return error.toString();
    }

    /**
     * 找出新增的供应商
     *
     * @param oldSupplierList
     * @param excelList
     * @param userName
     * @param bidActivityCode
     * @return
     */
    private List<JbxtUserParam> getAddSupplierList(List<UserDO> oldSupplierList, List<JbxtUserParam> excelList, String userName, String bidActivityCode) {
        if ( CollectionUtils.isEmpty(excelList)) {
            return Collections.emptyList();
        }

        if (CollectionUtils.isEmpty(oldSupplierList)) {
            excelList.stream().forEach(excelSupplier->{
                excelSupplier.setCode(worker.nextId());
                excelSupplier.setCreator(userName);
                excelSupplier.setCreatedTime(new Date());
                excelSupplier.setUpdator(userName);
                excelSupplier.setUpdatedTime(new Date());
                excelSupplier.setUsername(manageService.getUserName());
                excelSupplier.setPassword(manageService.getPassword());
                excelSupplier.setAdmin("0");// 表示普通成员
                excelSupplier.setActivityCode(bidActivityCode);
                excelSupplier.setCodeName(getCodeName(bidActivityCode,userName));
                excelSupplier.setLoginStatus(0);
                excelSupplier.setSendMail(0);
                excelSupplier.setSendSms(0);
            });
            return excelList;
        }
        Map<String, UserDO> oldSupplierMap = Maps.uniqueIndex(oldSupplierList, o -> o.getSupplierName());
        List<JbxtUserParam> addSupplierList = Lists.newArrayList();
        excelList.stream().forEach(excelSupplier -> {
            if (!oldSupplierMap.containsKey(excelSupplier.getSupplierName())) {
                excelSupplier.setCode(worker.nextId());
                excelSupplier.setCreator(userName);
                excelSupplier.setCreatedTime(new Date());
                excelSupplier.setUpdator(userName);
                excelSupplier.setUpdatedTime(new Date());
                excelSupplier.setUsername(manageService.getUserName());
                excelSupplier.setPassword(manageService.getPassword());
                excelSupplier.setAdmin("0");// 表示普通成员
                excelSupplier.setActivityCode(bidActivityCode);
                excelSupplier.setCodeName(getCodeName(bidActivityCode,userName));
                excelSupplier.setLoginStatus(0);
                excelSupplier.setSendMail(0);
                excelSupplier.setSendSms(0);
                addSupplierList.add(excelSupplier);
            }
        });
        return addSupplierList;
    }

    /**
     * 获取更新的供应商信息
     *
     * @param oldSupplierList
     * @param excelList
     * @return
     */
    private List<JbxtUserParam> getUpdateSupplierList(List<UserDO> oldSupplierList, List<JbxtUserParam> excelList, String userName, String bidActivityCode) {
        if (CollectionUtils.isEmpty(oldSupplierList) || CollectionUtils.isEmpty(excelList)) {
            return Collections.emptyList();
        }
        Map<String, UserDO> oldSupplierMap = Maps.uniqueIndex(oldSupplierList, o -> o.getSupplierName());
        List<JbxtUserParam> updateSupplierList = Lists.newArrayList();
        excelList.stream().forEach(excelSupplier -> {
            if (oldSupplierMap.containsKey(excelSupplier.getSupplierName())) {

                UserDO userDO = oldSupplierMap.get(excelSupplier.getSupplierName());
                String newPhone = excelSupplier.getPhone();
                String oldPhone = userDO.getPhone();

                String newEmailAddress = excelSupplier.getEmailAddress();
                String oldEmailAddress = userDO.getEmailAddress();

                String newContactPerson = excelSupplier.getContactPerson();
                String oldContactPerson = userDO.getContactPerson();

                if (userDO.getSupplierName().equals(excelSupplier.getSupplierName()) && (
                        !StringUtils.equals(newPhone, oldPhone)
                                || !StringUtils.equals(newEmailAddress, oldEmailAddress)
                                || !StringUtils.equals(newContactPerson, oldContactPerson))) {
                    JbxtUserParam userParam = new JbxtUserParam();
                    CopyUtil.copyPropertiesExceptEmpty(userDO, userParam);
                    userParam.setPhone(excelSupplier.getPhone());
                    userParam.setEmailAddress(excelSupplier.getEmailAddress());
                    userParam.setContactPerson(excelSupplier.getContactPerson());
                    userParam.setSendSms(0);
                    userParam.setSendMail(0);
                    updateSupplierList.add(userParam);
                }
            }
        });
        return updateSupplierList;
    }

    /**
     * 获取删除的供应商信息
     *
     * @param oldSupplierList
     * @param excelList
     * @return
     */
    private List<String> getDeleteSupplierList(List<UserDO> oldSupplierList, List<JbxtUserParam> excelList) {
        if (CollectionUtils.isEmpty(oldSupplierList) || CollectionUtils.isEmpty(excelList)) {
            return Collections.emptyList();
        }
        Map<String, JbxtUserParam> excelSupplierMap = Maps.uniqueIndex(excelList, o -> o.getSupplierName());
        List<String> deleteCodes = Lists.newArrayList();
        oldSupplierList.stream().forEach(oldSupplier -> {
            if (!excelSupplierMap.containsKey(oldSupplier.getSupplierName())) {
                deleteCodes.add(oldSupplier.getCode());
            }
        });
        return deleteCodes;
    }

    /**
     * 获取代号
     *
     * @param bidActivityCode
     * @return
     */
    private String getCodeName(String bidActivityCode, String userName) {

        String codeName = null;
        for (int i = 1; i <= 20; i++) {
            try {
                codeName = CodeNameEnum.findByCode(i);
            } catch (BaseException e) {
                log.error("There is an error of getting CodeName【{}】,【{}】", e.getMessage(), e);
                e.printStackTrace();
            }
            List<UserDO> userDOList = userDao.getUser(JbxtUserParam.builder().username(userName).activityCode(bidActivityCode).codeName(codeName).build());
            if (CollectionUtils.isEmpty(userDOList) && !addingCodeNameMap.containsKey(codeName)) {
                addingCodeNameMap.put(codeName, codeName);
                break;
            }
        }
        return codeName;
    }

    /**
     * 校验导入的供应商信息
     */
    private class SupplierExcelParamValidateResult {
        private Map<String, Object> userMap;
        private JbxtSupplierExcelParam supplierExcelParam;
        private StringBuilder errorMsg;

        public SupplierExcelParamValidateResult(Map<String, Object> userMap) {
            this.userMap = userMap;
        }

        public JbxtSupplierExcelParam getSupplierExcelParam() {
            return supplierExcelParam;
        }

        public StringBuilder getErrorMsg() {
            return errorMsg;
        }

        public SupplierExcelParamValidateResult invoke() {
            supplierExcelParam = new JbxtSupplierExcelParam();
            errorMsg = new StringBuilder("");

            Tuple<String, JbxtSupplierExcelParam> paramTuple = ImportExcelValidateMapUtil.validateField(supplierExcelParam, userMap);
            errorMsg.append(paramTuple._1);
            supplierExcelParam = paramTuple._2;
            errorMsg.append(validateExcelParam(supplierExcelParam));
            return this;
        }
    }

    /**
     * 获取供应商errorList
     * @param list
     * @param commErrorMsg
     * @return
     */
    private List<JbxtSupplierExcelParam> getErrorSupplierExcelParams(List<Map<String, Object>> list, String commErrorMsg) {
        List<JbxtSupplierExcelParam> errorList = Lists.newArrayList();
        for (Map<String, Object> userMap : list) {
            SupplierExcelParamValidateResult SupplierExcelParamValidateResult = new SupplierExcelParamValidateResult(userMap).invoke();
            JbxtSupplierExcelParam supplierExcelParam = SupplierExcelParamValidateResult.getSupplierExcelParam();
            supplierExcelParam.setErrorMsg(commErrorMsg );
            StringBuilder errorMsg = SupplierExcelParamValidateResult.getErrorMsg();

            if (StringUtils.isNotEmpty(errorMsg.toString())) {
                supplierExcelParam.setErrorMsg(errorMsg.toString());
            }
            errorList.add(supplierExcelParam);
        }
        return errorList;
    }
}


