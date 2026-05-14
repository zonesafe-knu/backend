package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.AutoLabelJob;
import me.zonesafe.zonesafe_be.domain.Model;
import me.zonesafe.zonesafe_be.dto.AutoLabelJobResponseDto;
import me.zonesafe.zonesafe_be.dto.AutoLabelRequestDto;
import me.zonesafe.zonesafe_be.dto.ExportRequestDto;
import me.zonesafe.zonesafe_be.dto.ModelResponseDto;
import me.zonesafe.zonesafe_be.enums.AutoLabelJobStatus;
import me.zonesafe.zonesafe_be.enums.ModelFormat;
import me.zonesafe.zonesafe_be.repository.AutoLabelJobRepository;
import me.zonesafe.zonesafe_be.repository.ModelRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelService {
    private final ModelRepository modelRepository;
    private final AutoLabelJobRepository autoLabelJobRepository;
    private final ModelMapper modelMapper;

    //모델 목록 조회
    public List<ModelResponseDto> getModels() {
        return modelRepository.findAll().stream()
                .map(m -> modelMapper.map(m, ModelResponseDto.class))
                .collect(Collectors.toList());
    }

    //활성 모델 변경
    @Transactional
    public ModelResponseDto activateModel(Long modelId) {
        Model target = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("해당 모델이 존재하지 않습니다. ID: " + modelId));

        modelRepository.deactivateAll();
        target.setActive(true);

        return modelMapper.map(target, ModelResponseDto.class);
    }

    //ONNX 변환 작업 (Export)
    @Transactional
    public ModelResponseDto exportModel(Long modelId, ExportRequestDto request) {
        Model source = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("해당 모델이 존재하지 않습니다. ID: " + modelId));

        ModelFormat targetFormat = request.getFormat();
        if (targetFormat == null) {
            throw new IllegalArgumentException("변환할 format이 지정되지 않았습니다.");
        }

        Model exported = new Model();
        exported.setName(source.getName());
        exported.setVersion(source.getVersion());
        exported.setFormat(targetFormat);
        exported.setMAP50(source.getMAP50());
        exported.setMAP50_95(source.getMAP50_95());
        exported.setFps(source.getFps());
        exported.setActive(false);
        exported.setCreatedAt(ZonedDateTime.now());

        Model saved = modelRepository.save(exported);
        return modelMapper.map(saved, ModelResponseDto.class);
    }

    //오토 라벨링 작업 생성
    @Transactional
    public AutoLabelJobResponseDto createAutoLabelJob(AutoLabelRequestDto request) {
        if (request.getModelId() == null || request.getImageSetId() == null) {
            throw new IllegalArgumentException("modelId, imageSetId는 필수입니다.");
        }
        modelRepository.findById(request.getModelId())
                .orElseThrow(() -> new IllegalArgumentException("해당 모델이 존재하지 않습니다. ID: " + request.getModelId()));

        AutoLabelJob job = new AutoLabelJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setModelId(request.getModelId());
        job.setImageSetId(request.getImageSetId());
        job.setConfidenceThreshold(request.getConfidenceThreshold() != null ? request.getConfidenceThreshold() : 0.5);
        job.setStatus(AutoLabelJobStatus.PENDING);
        job.setProgress(0.0);
        job.setTotalImages(0);
        job.setProcessed(0);
        job.setCreatedAt(ZonedDateTime.now());

        AutoLabelJob saved = autoLabelJobRepository.save(job);
        return toJobDto(saved);
    }

    //오토 라벨링 작업 상태 조회
    public AutoLabelJobResponseDto getAutoLabelJob(String jobId) {
        AutoLabelJob job = autoLabelJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("해당 작업이 존재하지 않습니다. ID: " + jobId));
        return toJobDto(job);
    }

    private AutoLabelJobResponseDto toJobDto(AutoLabelJob job) {
        AutoLabelJobResponseDto dto = new AutoLabelJobResponseDto();
        dto.setJobId(job.getJobId());
        dto.setStatus(job.getStatus());
        dto.setProgress(job.getProgress());
        dto.setTotalImages(job.getTotalImages());
        dto.setProcessed(job.getProcessed());
        return dto;
    }
}