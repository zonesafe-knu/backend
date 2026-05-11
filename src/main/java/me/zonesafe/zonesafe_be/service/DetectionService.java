package me.zonesafe.zonesafe_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.DetectionSnapshotResponseDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DetectionService {
    // TODO: 추후 AI 엔진이 쏴주는 Redis 데이터를 조회하는 로직으로 대체해야 합니다!
    // 현재는 API 스펙 연동 테스트를 위해 명세서와 동일한 더미 데이터를 반환합니다.

    //Redis에 문자열(JSON) 형태로 접근하기 위한 템플릿
    private final StringRedisTemplate redisTemplate;
    //JSON 문자열을 자바 객체(DTO)로 바꿔주는 변환기
    private final ObjectMapper objectMapper;

    public DetectionSnapshotResponseDto getDetectionSnapshot(Long cameraId){
        // 1. Redis에서 찾을 Key 조합 (예: "camera:1:latest_detection")
        // (이 Key 이름은 AI 파트 담당자와 반드시 맞춰야 합니다!)
        String redisKey = "camera:" + cameraId + ":latest_detection";

        // 2. Redis에서 JSON 문자열 꺼내기
        String jsonResult = redisTemplate.opsForValue().get(redisKey);

        // 3. 데이터가 없는 경우 (AI 엔진이 꺼져있거나, 아직 탐지된 게 없을 때)
        if (jsonResult == null || jsonResult.isEmpty()) {
            // 빈 데이터를 반환하거나 예외를 던집니다. (프론트가 에러 나지 않게 null 리턴이 안전할 수 있습니다)
            return null;
        }

        // 4. 꺼내온 JSON 문자열을 우리가 만든 DTO 클래스로 예쁘게 변환
        try{
            return objectMapper.readValue(jsonResult, DetectionSnapshotResponseDto.class);
        } catch (JsonProcessingException e) {
            // JSON 변환 중 에러가 발생한 경우
            throw new RuntimeException("Redis 데이터 파싱 실패: " + e.getMessage());
        }

    }

//    public DetectionSnapshotResponseDto getDetectionSnapshot(Long cameraId) {
//        return DetectionSnapshotResponseDto.builder()
//                .cameraId(cameraId)
//                .frameTimestamp(ZonedDateTime.now())
//                .fps(28.4)
//                .modelVersion("yolov8m_zonesafe_v3")
//                .objects(List.of(
//                        DetectionSnapshotResponseDto.DetectedObjectDto.builder()
//                                .trackId(17L)
//                                .label("worker")
//                                .bbox(List.of(340, 210, 420, 470))
//                                .confidence(0.91)
//                                .inRoi(List.of(10L))
//                                .build(),
//                        DetectionSnapshotResponseDto.DetectedObjectDto.builder()
//                                .trackId(33L)
//                                .label("forklift")
//                                .bbox(List.of(430, 250, 610, 500))
//                                .confidence(0.94)
//                                .inRoi(List.of(10L))
//                                .build()
//                ))
//                .build();
//    }
}
