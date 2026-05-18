package com.example.liveklass.klass;

import com.example.liveklass.global.BusinessException;
import com.example.liveklass.user.User;
import com.example.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KlassServiceTest {

    @Mock
    private KlassRepository klassRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KlassService klassService;

    private Klass openKlass;
    private Klass draftKlass;

    @BeforeEach
    void setUp() {
        given(userRepository.findById(anyLong()))
                .willReturn(Optional.of(User.builder().name("테스트유저").build()));

        openKlass = Klass.builder()
                .creatorId(1L)
                .title("Spring Boot 강의")
                .description("설명")
                .price(BigDecimal.valueOf(50000))
                .capacity(10)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(30))
                .build();

        draftKlass = Klass.builder()
                .creatorId(1L)
                .title("Draft 강의")
                .description("설명")
                .price(BigDecimal.valueOf(10000))
                .capacity(5)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .build();
    }

    @Test
    void 강의_생성_성공() {
        KlassCreateRequest request = new KlassCreateRequest();
        request.setTitle("테스트 강의");
        request.setDescription("설명");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setCapacity(20);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(31));

        Klass saved = Klass.builder()
                .creatorId(1L)
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .capacity(request.getCapacity())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        given(klassRepository.save(any(Klass.class))).willReturn(saved);

        KlassResponse response = klassService.createKlass(1L, request);

        assertThat(response.getTitle()).isEqualTo("테스트 강의");
        assertThat(response.getCapacity()).isEqualTo(20);
    }

    @Test
    void 강의_생성_시작일이_종료일_이후면_예외() {
        KlassCreateRequest request = new KlassCreateRequest();
        request.setTitle("테스트");
        request.setDescription("설명");
        request.setPrice(BigDecimal.valueOf(1000));
        request.setCapacity(5);
        request.setStartDate(LocalDate.now().plusDays(10));
        request.setEndDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> klassService.createKlass(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 강의_목록_전체조회시_모든상태_포함() {
        openKlass.updateStatus(ClassStatus.OPEN);

        Klass closedKlass = Klass.builder()
                .creatorId(2L)
                .title("Closed 강의")
                .description("설명")
                .price(BigDecimal.ZERO)
                .capacity(5)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .build();
        closedKlass.updateStatus(ClassStatus.OPEN);
        closedKlass.updateStatus(ClassStatus.CLOSED);

        given(klassRepository.findAll())
                .willReturn(List.of(openKlass, closedKlass, draftKlass));

        List<KlassResponse> result = klassService.getKlasses(null);

        assertThat(result).hasSize(3);
    }

    @Test
    void 강의_목록_status_OPEN_필터() {
        openKlass.updateStatus(ClassStatus.OPEN);
        given(klassRepository.findByStatus(ClassStatus.OPEN)).willReturn(List.of(openKlass));

        List<KlassResponse> result = klassService.getKlasses(ClassStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ClassStatus.OPEN);
    }

    @Test
    void 강의_목록_status_DRAFT_필터_결과반환() {
        given(klassRepository.findByStatus(ClassStatus.DRAFT)).willReturn(List.of(draftKlass));

        List<KlassResponse> result = klassService.getKlasses(ClassStatus.DRAFT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ClassStatus.DRAFT);
    }

    @Test
    void 강의_상태_변경_CLOSED_후_변경시도_400() {
        openKlass.updateStatus(ClassStatus.OPEN);
        openKlass.updateStatus(ClassStatus.CLOSED);
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        KlassStatusUpdateRequest request = new KlassStatusUpdateRequest();
        request.setStatus(ClassStatus.OPEN);

        assertThatThrownBy(() -> klassService.updateStatus(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 강의_단건_조회_성공() {
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        KlassResponse response = klassService.getKlass(1L);

        assertThat(response.getTitle()).isEqualTo("Spring Boot 강의");
    }

    @Test
    void 강의_단건_조회_없으면_404() {
        given(klassRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> klassService.getKlass(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 강의_상태_변경_권한없으면_403() {
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        KlassStatusUpdateRequest request = new KlassStatusUpdateRequest();
        request.setStatus(ClassStatus.CLOSED);

        assertThatThrownBy(() -> klassService.updateStatus(999L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 강의_상태_변경_성공() {
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        KlassStatusUpdateRequest request = new KlassStatusUpdateRequest();
        request.setStatus(ClassStatus.OPEN);

        KlassResponse response = klassService.updateStatus(1L, 1L, request);

        assertThat(response.getStatus()).isEqualTo(ClassStatus.OPEN);
    }
}
