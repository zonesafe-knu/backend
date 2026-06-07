package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.AlarmStatusChangedEvent;
import me.zonesafe.zonesafe_be.repository.AlarmRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

// AlarmService 내부에서 호출 시 self-invocation 으로 @Transactional 이 안 먹히는 문제를 우회하기 위한 별도 컴포넌트
@Component
@RequiredArgsConstructor
public class AlarmClipAttacher {
    private final AlarmRepository alarmRepository;
    private final AlarmEventPublisher alarmEventPublisher;

    @Transactional
    public void attach(Long alarmId, Long clipId) {
        alarmRepository.findById(alarmId).ifPresent(a -> {
            a.setClipId(clipId);
            alarmEventPublisher.publishStatusChange(AlarmStatusChangedEvent.builder()
                    .alarmId(alarmId)
                    .status(a.getStatus())
                    .comment(a.getComment())
                    .clipId(clipId)
                    .changedAt(ZonedDateTime.now())
                    .build());
        });
    }
}