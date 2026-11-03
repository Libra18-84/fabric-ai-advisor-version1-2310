
package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.repository.FabricRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
@Service
public class FabricService {

    private final FabricRepository fabricRepository;

    public FabricService(FabricRepository fabricRepository) {
        this.fabricRepository = fabricRepository;
    }

    public int getTotalFabrics() {
        return fabricRepository.countFabrics();
    }

    public List<Map<String, Object>> getAllFabrics() {
        return fabricRepository.findAllFabrics();
    }

    public List<Map<String, Object>> getFabricsByBudget(
            double maxBudget) {

        if (!Double.isFinite(maxBudget) || maxBudget < 0) {
            throw new IllegalArgumentException(
                    "Ngan sach khong hop le"
            );
        }

        return fabricRepository.findFabricsByBudget(maxBudget);
    }

    public List<Map<String, Object>> getFabricsByGarment(
            String garment) {

        if (garment == null || garment.isBlank()) {
            throw new IllegalArgumentException(
                    "Loai trang phuc khong hop le"
            );
        }

        return fabricRepository.findFabricsByGarment(
                garment.trim()
        );
    }

    public List<Map<String, Object>> getFabricsByGarmentAndBudget(
            String garment, double maxBudget) {

        if (garment == null || garment.isBlank()) {
            throw new IllegalArgumentException(
                    "Loai trang phuc khong hop le"
            );
        }

        if (!Double.isFinite(maxBudget) || maxBudget < 0) {
            throw new IllegalArgumentException(
                    "Ngan sach khong hop le"
            );
        }

        return fabricRepository.findFabricsByGarmentAndBudget(
                garment.trim(),
                maxBudget
        );
    }

}
