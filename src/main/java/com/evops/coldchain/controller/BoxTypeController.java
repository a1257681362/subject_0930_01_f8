package com.evops.coldchain.controller;

import com.evops.coldchain.dto.BoxTypeCreateRequest;
import com.evops.coldchain.entity.BoxType;
import com.evops.coldchain.service.BoxTypeService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 保温箱箱型主档（V8）：报价维度“箱型”的建档入口。
 */
@RestController
@RequestMapping("/api/cold-chain/box-types")
public class BoxTypeController {

    private final BoxTypeService boxTypeService;

    public BoxTypeController(BoxTypeService boxTypeService) {
        this.boxTypeService = boxTypeService;
    }

    @PostMapping
    public ApiResponse<BoxType> create(@Valid @RequestBody BoxTypeCreateRequest req) {
        return ApiResponse.ok(boxTypeService.create(req));
    }

    @GetMapping
    public ApiResponse<List<BoxType>> list() {
        return ApiResponse.ok(boxTypeService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<BoxType> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(boxTypeService.requireBoxType(id));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<BoxType> revoke(@PathVariable("id") Long id) {
        return ApiResponse.ok(boxTypeService.revoke(id));
    }
}
