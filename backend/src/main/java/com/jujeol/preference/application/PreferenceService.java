package com.jujeol.preference.application;

import com.jujeol.drink.drink.domain.Drink;
import com.jujeol.drink.drink.domain.repository.DrinkRepository;
import com.jujeol.drink.drink.exception.NotFoundDrinkException;
import com.jujeol.drink.recommend.infrastructure.slope.DataMatrix;
import com.jujeol.drink.recommend.infrastructure.slope.DataModel;
import com.jujeol.member.member.application.dto.PreferenceDto;
import com.jujeol.preference.domain.Preference;
import com.jujeol.preference.domain.PreferenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final DrinkRepository drinkRepository;
    private final DataMatrix dataMatrix;

    public Page<Preference> showPreferenceByMemberId(Long memberId, Pageable pageable) {
        return preferenceRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    public Preference showByMemberIdAndDrink(Long memberId, Drink drink) {
        return preferenceRepository
                .findByMemberIdAndDrinkId(memberId, drink.getId())
                .orElseGet(() -> Preference.anonymousPreference(drink));
    }

    public Preference showByMemberIdAndDrink(Long memberId, Long drinkId) {
        return preferenceRepository
                .findByMemberIdAndDrinkId(memberId, drinkId)
                .orElseGet(() -> Preference.anonymousPreference(drinkId));
    }

    @Transactional
    public void createOrUpdatePreference(
            Long memberId,
            Long drinkId,
            PreferenceDto preferenceDto
    ) {
        Drink drink = drinkRepository.findById(drinkId)
                .orElseThrow(NotFoundDrinkException::new);

        final DataModel dataModel = new DataModel(memberId, drinkId, preferenceDto.getPreferenceRate());

        preferenceRepository
                .findByMemberIdAndDrinkId(memberId, drink.getId())
                .ifPresentOrElse(exist -> {
                    exist.updateRate(preferenceDto.getPreferenceRate());
                    dataMatrix.updateData(
                            dataModel);
                        },
                        () -> {
                            Preference newPreference = Preference
                                    .create(memberId, drink, preferenceDto.getPreferenceRate());
                            preferenceRepository.save(newPreference);
                            dataMatrix.addData(dataModel);
                        }
                );

        updatePreferenceAverage(drinkId, drink);
    }

    @Transactional
    public void deletePreference(Long memberId, Long drinkId) {
        Drink drink = drinkRepository.findById(drinkId)
                .orElseThrow(NotFoundDrinkException::new);

        preferenceRepository.deleteByMemberIdAndDrinkId(memberId, drinkId);

        dataMatrix.removeData(memberId, drinkId);
        updatePreferenceAverage(drinkId, drink);
    }

    private void updatePreferenceAverage(Long drinkId, Drink drink) {
        Double average = preferenceRepository.averageOfPreferenceRate(drinkId).orElse(0.0);
        drink.updateAverage(average);
    }

    public void deletePreferenceByDrinkId(Long id) {
        preferenceRepository.deleteByDrinkId(id);
        dataMatrix.resetData();
    }

    public List<Preference> showByMemberIdAndDrinks(Long memberId, List<Long> drinkIds) {
        return preferenceRepository.showByMemberIdAndDrinks(memberId, drinkIds);
    }
}
