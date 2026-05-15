package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.ClipResponseDto;
import me.zonesafe.zonesafe_be.dto.PageResponseDto;
import me.zonesafe.zonesafe_be.service.ClipService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clips")
@RequiredArgsConstructor
public class ClipController {
    private final ClipService clipService;

    //클립 목록 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, PageResponseDto<ClipResponseDto>> getClips(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ClipResponseDto> pageResult = clipService.getClips(cameraId, from, to, pageable);
        return Map.of("data", new PageResponseDto<>(pageResult));
    }

    //클립 다운로드
    @GetMapping("/{clipId}/download")
    public ResponseEntity<Resource> downloadClip(@PathVariable Long clipId) {
        Resource resource = clipService.loadClipResource(clipId);
        String filename = "clip_" + clipId + ".mp4";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
