package com.example.liveklass.klass;

import com.example.liveklass.global.BusinessException;
import com.example.liveklass.global.ErrorCode;
import com.example.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KlassService {

    private final KlassRepository klassRepository;
    private final UserRepository userRepository;

    @Transactional
    public KlassResponse createKlass(Long creatorId, KlassCreateRequest request) {
        userRepository.findById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!request.getStartDate().isBefore(request.getEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Klass klass = Klass.builder()
                .creatorId(creatorId)
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .capacity(request.getCapacity())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        return KlassResponse.from(klassRepository.save(klass));
    }

    @Transactional(readOnly = true)
    public List<KlassResponse> getKlasses(ClassStatus status) {
        List<Klass> klasses = (status != null)
                ? klassRepository.findByStatus(status)
                : klassRepository.findAll();
        return klasses.stream().map(KlassResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public KlassResponse getKlass(Long klassId) {
        Klass klass = klassRepository.findById(klassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KLASS_NOT_FOUND));
        return KlassResponse.from(klass);
    }

    @Transactional
    public KlassResponse updateStatus(Long creatorId, Long klassId, KlassStatusUpdateRequest request) {
        Klass klass = klassRepository.findById(klassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KLASS_NOT_FOUND));
        if (!klass.getCreatorId().equals(creatorId)) {
            throw new BusinessException(ErrorCode.KLASS_ACCESS_DENIED);
        }
        klass.updateStatus(request.getStatus());
        return KlassResponse.from(klass);
    }
}
