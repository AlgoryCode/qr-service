package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.model.nutrition.NutritionNutrientEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NutritionFactsService {

    private final ObjectMapper objectMapper;

    public void validateForCreate(NutritionFacts nutrition) {
        if (nutrition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Besin ùgesi bilgisi zorunludur");
        }
        if (nutrition.getBasis() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Besin ùgesi birimi zorunludur");
        }
        requireAmount(nutrition.getEnergyKj(), "energyKj");
        requireAmount(nutrition.getEnergyKcal(), "energyKcal");
        requireAmount(nutrition.getFat(), "fat");
        requireAmount(nutrition.getCarbohydrate(), "carbohydrate");
        requireAmount(nutrition.getFibre(), "fibre");
        requireAmount(nutrition.getProtein(), "protein");
        requireAmount(nutrition.getSalt(), "salt");
        validateOptionalAmount(nutrition.getSaturatedFat(), "saturatedFat");
        validateOptionalAmount(nutrition.getSugars(), "sugars");
        validateOptionalAmount(nutrition.getPolyols(), "polyols");
        validateOptionalAmount(nutrition.getStarch(), "starch");
        validateEntries(nutrition.getVitaminsAndMinerals(), "vitaminsAndMinerals");
        validateEntries(nutrition.getOtherNutrients(), "otherNutrients");
    }

    public NutritionFacts merge(NutritionFacts existing, NutritionFacts patch) {
        if (patch == null) {
            return existing;
        }
        validateEnergyPairOnPatch(patch);
        NutritionFacts target = existing == null ? new NutritionFacts() : copy(existing);
        if (patch.getBasis() != null) {
            target.setBasis(patch.getBasis());
        }
        if (patch.getEnergyKj() != null) {
            target.setEnergyKj(patch.getEnergyKj());
        }
        if (patch.getEnergyKcal() != null) {
            target.setEnergyKcal(patch.getEnergyKcal());
        }
        if (patch.getFat() != null) {
            target.setFat(patch.getFat());
        }
        if (patch.getSaturatedFat() != null) {
            target.setSaturatedFat(patch.getSaturatedFat());
        }
        if (patch.getCarbohydrate() != null) {
            target.setCarbohydrate(patch.getCarbohydrate());
        }
        if (patch.getSugars() != null) {
            target.setSugars(patch.getSugars());
        }
        if (patch.getPolyols() != null) {
            target.setPolyols(patch.getPolyols());
        }
        if (patch.getStarch() != null) {
            target.setStarch(patch.getStarch());
        }
        if (patch.getFibre() != null) {
            target.setFibre(patch.getFibre());
        }
        if (patch.getProtein() != null) {
            target.setProtein(patch.getProtein());
        }
        if (patch.getSalt() != null) {
            target.setSalt(patch.getSalt());
        }
        if (patch.getVitaminsAndMinerals() != null) {
            target.setVitaminsAndMinerals(copyEntries(patch.getVitaminsAndMinerals()));
        }
        if (patch.getOtherNutrients() != null) {
            target.setOtherNutrients(copyEntries(patch.getOtherNutrients()));
        }
        validateMergedState(target);
        return target;
    }

    public NutritionFacts parseFromRaw(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(raw, NutritionFacts.class);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geùersiz besin ùgesi bilgisi");
        }
    }

    private void validateMergedState(NutritionFacts nutrition) {
        if (nutrition.getBasis() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Besin ùgesi birimi zorunludur");
        }
        requireAmount(nutrition.getEnergyKj(), "energyKj");
        requireAmount(nutrition.getEnergyKcal(), "energyKcal");
        requireAmount(nutrition.getFat(), "fat");
        requireAmount(nutrition.getCarbohydrate(), "carbohydrate");
        requireAmount(nutrition.getFibre(), "fibre");
        requireAmount(nutrition.getProtein(), "protein");
        requireAmount(nutrition.getSalt(), "salt");
        validateOptionalAmount(nutrition.getSaturatedFat(), "saturatedFat");
        validateOptionalAmount(nutrition.getSugars(), "sugars");
        validateOptionalAmount(nutrition.getPolyols(), "polyols");
        validateOptionalAmount(nutrition.getStarch(), "starch");
        validateEntries(nutrition.getVitaminsAndMinerals(), "vitaminsAndMinerals");
        validateEntries(nutrition.getOtherNutrients(), "otherNutrients");
    }

    private void validateEnergyPairOnPatch(NutritionFacts patch) {
        boolean hasKj = patch.getEnergyKj() != null;
        boolean hasKcal = patch.getEnergyKcal() != null;
        if (hasKj != hasKcal) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enerji de?eri hem kJ hem kcal olarak verilmelidir");
        }
    }

    private void requireAmount(BigDecimal value, String field) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " zorunludur");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " negatif olamaz");
        }
    }

    private void validateOptionalAmount(BigDecimal value, String field) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " negatif olamaz");
        }
    }

    private void validateEntries(List<NutritionNutrientEntry> entries, String field) {
        if (entries == null) {
            return;
        }
        for (NutritionNutrientEntry entry : entries) {
            if (entry == null || entry.getName() == null || entry.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " ad? zorunludur");
            }
            if (entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " de?eri negatif olamaz");
            }
        }
    }

    private NutritionFacts copy(NutritionFacts source) {
        return NutritionFacts.builder()
                .basis(source.getBasis())
                .energyKj(source.getEnergyKj())
                .energyKcal(source.getEnergyKcal())
                .fat(source.getFat())
                .saturatedFat(source.getSaturatedFat())
                .carbohydrate(source.getCarbohydrate())
                .sugars(source.getSugars())
                .polyols(source.getPolyols())
                .starch(source.getStarch())
                .fibre(source.getFibre())
                .protein(source.getProtein())
                .salt(source.getSalt())
                .vitaminsAndMinerals(copyEntries(source.getVitaminsAndMinerals()))
                .otherNutrients(copyEntries(source.getOtherNutrients()))
                .build();
    }

    private List<NutritionNutrientEntry> copyEntries(List<NutritionNutrientEntry> entries) {
        if (entries == null) {
            return new ArrayList<>();
        }
        List<NutritionNutrientEntry> copy = new ArrayList<>(entries.size());
        for (NutritionNutrientEntry entry : entries) {
            copy.add(NutritionNutrientEntry.builder()
                    .name(entry.getName())
                    .value(entry.getValue())
                    .unit(entry.getUnit())
                    .build());
        }
        return copy;
    }
}
