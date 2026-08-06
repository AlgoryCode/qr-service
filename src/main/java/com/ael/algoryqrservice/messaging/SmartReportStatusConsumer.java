package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.service.SmartReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmartReportStatusConsumer {

    private final SmartReportService smartReportService;

    @RabbitListener(queues = "#{smartReportRabbitProperties.eventsQueue}")
    public void onSmartReportStatus(SmartReportStatusMessage event) {
        log.info(
                "Smart report status event consumed. jobId={} status={}",
                event == null ? null : event.jobId(),
                event == null ? null : event.status()
        );
        try {
            smartReportService.applyStatusEvent(event);
            log.info(
                    "Smart report status event processed. processId={} status={}",
                    event.jobId(),
                    event.status()
            );
        } catch (IllegalArgumentException exception) {
            log.error(
                    "Smart report status event rejected. jobId={} status={} reason={}",
                    event == null ? null : event.jobId(),
                    event == null ? null : event.status(),
                    exception.getMessage(),
                    exception
            );
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            log.error(
                    "Smart report status event processing failed. jobId={} status={}",
                    event == null ? null : event.jobId(),
                    event == null ? null : event.status(),
                    exception
            );
            throw exception;
        }
    }
}
