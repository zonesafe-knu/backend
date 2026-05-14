package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Clip;
import me.zonesafe.zonesafe_be.dto.ClipResponseDto;
import me.zonesafe.zonesafe_be.repository.ClipRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClipService {
    private final ClipRepository clipRepository;
    private final ModelMapper modelMapper;

    //클립 목록 조회
    public Page<ClipResponseDto> getClips(Long cameraId, ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        Page<Clip> clips = clipRepository.findAllByFilter(cameraId, from, to, pageable);
        return clips.map(this::convertToDto);
    }

    private ClipResponseDto convertToDto(Clip clip) {
        ClipResponseDto dto = modelMapper.map(clip, ClipResponseDto.class);
        dto.setCameraId(clip.getCamera().getCameraId());
        dto.setDownloadUrl("/api/v1/clips/" + clip.getClipId() + "/download");
        dto.setStreamUrl("/api/v1/clips/" + clip.getClipId() + "/stream");
        dto.setThumbnailUrl("/api/v1/clips/" + clip.getClipId() + "/thumbnail");
        return dto;
    }
}
