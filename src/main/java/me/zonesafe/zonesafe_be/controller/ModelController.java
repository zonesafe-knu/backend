package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.AutoLabelJobResponseDto;
import me.zonesafe.zonesafe_be.dto.AutoLabelRequestDto;
import me.zonesafe.zonesafe_be.dto.ExportRequestDto;
import me.zonesafe.zonesafe_be.dto.ModelResponseDto;
import me.zonesafe.zonesafe_be.service.ModelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// TODO: Spring Security 도입 후 @PreAuthorize("hasRole('ADMIN')") 적용
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class ModelController {
    private final ModelService modelService;

    //모델 목록
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, List<ModelResponseDto>> getModels() {
        return Map.of("data", modelService.getModels());
    }

    //활성 모델 변경
    @PatchMapping("/{modelId}/activate")
    @ResponseStatus(HttpStatus.OK)
    public ModelResponseDto activateModel(@PathVariable Long modelId) {
        return modelService.activateModel(modelId);
    }

    //ONNX 변환 작업
    @PostMapping("/{modelId}/export")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelResponseDto exportModel(@PathVariable Long modelId,
                                        @RequestBody ExportRequestDto request) {
        return modelService.exportModel(modelId, request);
    }

    //오토 라벨링 작업 생성
    @PostMapping("/auto-label")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, AutoLabelJobResponseDto> createAutoLabelJob(@RequestBody AutoLabelRequestDto request) {
        return Map.of("data", modelService.createAutoLabelJob(request));
    }

    //오토 라벨링 작업 상태 조회
    @GetMapping("/auto-label/jobs/{jobId}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, AutoLabelJobResponseDto> getAutoLabelJob(@PathVariable String jobId) {
        return Map.of("data", modelService.getAutoLabelJob(jobId));
    }
}