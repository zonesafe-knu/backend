package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Clip;
import me.zonesafe.zonesafe_be.dto.ClipResponseDto;
import me.zonesafe.zonesafe_be.repository.ClipRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClipService {
    private final ClipRepository clipRepository;
    private final ModelMapper modelMapper;

    @Value("${clips.storage.base-path}")
    private String basePath;

    //클립 목록 조회
    public Page<ClipResponseDto> getClips(Long cameraId, ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        Page<Clip> clips = clipRepository.findAllByFilter(cameraId, from, to, pageable);
        return clips.map(this::convertToDto);
    }

    //클립 다운로드용 리소스 조회
    public Resource loadClipResource(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));

        Path path = resolvePath(clip.getFilePath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("클립 파일이 존재하지 않습니다. ID: " + clipId);
        }
        return resource;
    }

    //썸네일 리소스 조회
    public Resource loadThumbnailResource(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));

        if (clip.getThumbnailPath() == null || clip.getThumbnailPath().isBlank()) {
            throw new IllegalArgumentException("해당 클립의 썸네일이 없습니다. ID: " + clipId);
        }

        Path path = resolvePath(clip.getThumbnailPath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("썸네일 파일이 존재하지 않습니다. ID: " + clipId);
        }
        return resource;
    }

    public Clip getClip(Long clipId) {
        return clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));
    }

    //클립 삭제 (파일 + DB)
    @Transactional
    public void deleteClip(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("삭제하려는 클립이 존재하지 않습니다. ID: " + clipId));

        deleteFileQuietly(clip.getFilePath());
        deleteFileQuietly(clip.getThumbnailPath());

        clipRepository.delete(clip);
    }

    private void deleteFileQuietly(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return;
        try {
            Files.deleteIfExists(resolvePath(storedPath));
        } catch (IOException ignored) {
        }
    }

    private Path resolvePath(String storedPath) {
        Path stored = Paths.get(storedPath);
        return stored.isAbsolute() ? stored : Paths.get(basePath).resolve(storedPath).normalize();
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
