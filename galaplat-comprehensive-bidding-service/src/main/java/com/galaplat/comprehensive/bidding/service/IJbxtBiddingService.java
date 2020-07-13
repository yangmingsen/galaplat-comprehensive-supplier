package com.galaplat.comprehensive.bidding.service;

import java.io.Serializable;
import java.util.List;

import com.galaplat.base.core.common.exception.BaseException;
import com.galaplat.comprehensive.bidding.dao.dvos.JbxtBiddingDVO;
import com.galaplat.comprehensive.bidding.dao.dos.JbxtBiddingDO;
import com.galaplat.comprehensive.bidding.querys.JbxtBiddingQuery;
import com.galaplat.comprehensive.bidding.vos.JbxtBiddingVO;
import com.github.pagehelper.PageInfo;

 /**
 * 竞价表Service
 * @author esr
 * @date: 2020年06月17日
 */
public interface IJbxtBiddingService{

	//---------------------
	 /***
	  * insert
	  * @param record
	  * @return
	  */
	 int insertMinBidTableSelective(JbxtBiddingVO record);

	 /***
	  * 获取当前用户最小竞价
	  * @param userCode
	  * @param goodsId
	  * @param activityCode
	  * @return
	  */
	 JbxtBiddingDO selectMinBidTableBy(String userCode, Integer goodsId, String activityCode);

	 /***
	  * 获取当前竞品所有用户最小竞价
	  * @param goodsId
	  * @param activityCode
	  * @return
	  */
	 List<JbxtBiddingDVO> selectMinBidTableBy(Integer goodsId, String activityCode);

	 /***
	  * update
	  * @param record
	  * @return
	  */
	 int updateMinBidTableByPrimaryKeySelective(JbxtBiddingVO record);

	 public int deleteMinbidTableByGoodsIdAndActivityCode(Integer goodsId, String activityCode);

	//----------minBidTble opreation end-----------


	 List<JbxtBiddingDVO> getTheTopBids(Integer goodsId, String activityCode);


	 int deleteByGoodsIdAndActivityCode(Integer goodsId, String activityCode);

    /**
	 * 添加竞价表
	 */
	int insertJbxtBidding(JbxtBiddingVO jbxtbiddingVO);

	/**
	 * 更新竞价表信息
	 */
	int updateJbxtBidding(JbxtBiddingVO jbxtbiddingVO);


	 public JbxtBiddingDVO getUserMinBid(String userCode, Integer goodsId, String activityCode);


	 /**
	  * 获取当前竞品的最小提交价
	  * @param goodsId
	  * @param activityCode
	  * @return
	  */
	 public JbxtBiddingDVO getCurrentGoodsMinSubmitPrice(String userCode, Integer goodsId, String activityCode);


	 /***
	  * 根据userCode和activityCode获取对应的竞价信息(也就是该用户的竞价记录) 根据竞价ASC
	  * @param userCode
	  * @param activityCode
	  * @return
	  */
	 public List<JbxtBiddingDVO> findAllByUserCodeAndActivityCode(String userCode, String activityCode);

}