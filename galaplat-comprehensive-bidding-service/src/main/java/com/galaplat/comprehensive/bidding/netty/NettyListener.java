package com.galaplat.comprehensive.bidding.netty;

import com.galaplat.comprehensive.bidding.activity.ActivityMap;
import com.galaplat.comprehensive.bidding.activity.ActivityThread;
import com.galaplat.comprehensive.bidding.activity.queue.QueueHandlerThread;
import com.galaplat.comprehensive.bidding.dao.dos.JbxtGoodsDO;
import com.galaplat.comprehensive.bidding.dao.dvos.JbxtActivityDVO;
import com.galaplat.comprehensive.bidding.service.IJbxtActivityService;
import com.galaplat.comprehensive.bidding.service.IJbxtGoodsService;
import com.galaplat.comprehensive.bidding.utils.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NettyListener implements ApplicationListener<ContextRefreshedEvent> {


    private final Logger LOGGER = LoggerFactory.getLogger(NettyListener.class);

    @Autowired
    private WebSocketServer websocketServer;

    private boolean isInit = false;

    @Autowired
    private  SpringUtil springUtil;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!isInit) {
            websocketServer.start();
            LOGGER.info("onApplicationEvent(msg): WebSocket started");

            IJbxtActivityService iJbxtActivityServiceBeans = springUtil.getBean(IJbxtActivityService.class);
            ActivityMap activityMap = springUtil.getBean(ActivityMap.class);
            List<JbxtActivityDVO> lists = iJbxtActivityServiceBeans.findAll();
            for (JbxtActivityDVO jbxtActivityDVO : lists) {
                if (jbxtActivityDVO.getStatus() == 3) {
                    IJbxtGoodsService iJbxtGoodsService = springUtil.getBean(IJbxtGoodsService.class);
                    JbxtGoodsDO activeGoods = iJbxtGoodsService.selectActiveGoods(jbxtActivityDVO.getCode());
                    if (activeGoods != null) {
                        ActivityThread currentActivity = new ActivityThread(jbxtActivityDVO.getCode(), activeGoods.getGoodsId().toString(), activeGoods.getTimeNum() * 60, 1);
                        activityMap.put(jbxtActivityDVO.getCode(), currentActivity);
                        currentActivity.start();
                        LOGGER.info("启动 " + jbxtActivityDVO.getCode() + " 活动");
                    }

                }
            }

            QueueHandlerThread queueHandler = QueueHandlerThread.getInstance();
            queueHandler.start();

            this.isInit = true;
        }

    }
}
