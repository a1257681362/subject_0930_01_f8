package com.evops.coldchain.controller;

import com.evops.coldchain.dto.CarrierCreateRequest;
import com.evops.coldchain.dto.CarrierRevokeRequest;
import com.evops.coldchain.entity.Carrier;
import com.evops.coldchain.service.CarrierService;
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
 * 冷链承运商主档（V8）：建档/列表/撤回。报价比选以稳定承运商为主体。
 */
@RestController
@RequestMapping("/api/cold-chain/carriers")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @PostMapping
    public ApiResponse<Carrier> create(@Valid @RequestBody CarrierCreateRequest req) {
        return ApiResponse.ok(carrierService.create(req));
    }

    @GetMapping
    public ApiResponse<List<Carrier>> list() {
        return ApiResponse.ok(carrierService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Carrier> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(carrierService.requireCarrier(id));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<Carrier> revoke(@PathVariable("id") Long id,
                                       @Valid @RequestBody CarrierRevokeRequest req) {
        return ApiResponse.ok(carrierService.revoke(id, req.getReason()));
    }
}
