package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.RoiRequestDto;
import me.zonesafe.zonesafe_be.dto.RoiResponseDto;
import me.zonesafe.zonesafe_be.service.RoiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rois")
@RequiredArgsConstructor
public class RoiController {
    private final RoiService roiService;

    //ROI 목록 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<RoiResponseDto> getRois(@RequestParam(required = false) Long cameraId) {
        return roiService.getRois(cameraId);
    }

    //ROI 상세 조회
    @GetMapping("/{roiId}")
    @ResponseStatus(HttpStatus.OK)
    public RoiResponseDto getRoiById(@PathVariable Long roiId) {
        return roiService.getRoiById(roiId);
    }

    //ROI 생성
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoiResponseDto createRoi(@RequestBody RoiRequestDto requestDto) {
        return roiService.createRoi(requestDto);
    }

    //ROI 수정
    @PutMapping("/{roiId}")
    @ResponseStatus(HttpStatus.OK)
    public RoiResponseDto updateRoi(@PathVariable Long roiId, @RequestBody RoiRequestDto requestDto) {
        return roiService.updateRoi(roiId, requestDto);
    }

    //ROI 활성화 토글
    @PatchMapping("/{roiId}/active")
    @ResponseStatus(HttpStatus.OK)
    public RoiResponseDto toggleActive(@PathVariable Long roiId) {
        return roiService.toggleActive(roiId);
    }

    //ROI 삭제
    @DeleteMapping("/{roiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoi(@PathVariable Long roiId) {
        roiService.deleteRoi(roiId);
    }
}
