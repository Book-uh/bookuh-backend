package com.bookuh.backend.service;

import com.bookuh.backend.dto.ReadingRecordRequest;
import com.bookuh.backend.dto.ReadingRecordResponse;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.ReadingActivity;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import com.bookuh.backend.repository.ReadingActivityRepository;
import com.bookuh.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReadingRecordService {

    private static final String RECORD_ADD = "READING_RECORD_ADD";

    private final ReadingActivityRepository readingActivityRepository;
    private final BookshelfItemRepository bookshelfItemRepository;
    private final ApiRequestRepository apiRequestRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ReadingRecordResponse> list(User user, String isbn, LocalDate date, String timeBand,
                                           Integer durationMinutes, Integer pagesRead) {
        return readingActivityRepository.findMatching(user, isbn, date, timeBand, durationMinutes, pagesRead)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WriteResult add(User user, ReadingRecordRequest input) {
        validate(input.getReadingDate(), input.getTimeBand(), input.getDurationMinutes(), input.getPagesRead());
        User lockedUser = lockUser(user);
        byte[] digest = hash(input);
        ApiRequest previous = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(lockedUser, RECORD_ADD, input.getActionId()).orElse(null);
        if (previous != null) {
            if (!Arrays.equals(previous.getPayloadDigest(), digest)) {
                throw new ActionConflictException();
            }
            return new WriteResult(previous.getResponseCode(), previous.getResultPayload());
        }

        BookshelfItem book = findBook(lockedUser, input.getIsbn());
        ApiRequest request = new ApiRequest(lockedUser, RECORD_ADD, input.getActionId(), digest);
        if (!"EXPLICIT_ADDITIONAL".equals(input.getAdditionIntent())) {
            ReadingActivity match = readingActivityRepository
                    .findFirstByBookshelfItemAndReadingDateAndDurationMinutesAndPagesReadOrderByIdDesc(
                            book, input.getReadingDate(), input.getDurationMinutes(), input.getPagesRead())
                    .orElse(null);
            if (match != null) {
                // 보류한 요청의 ID를 확인 버튼과 연결한다
                apiRequestRepository.save(request);
                ReadingRecordResponse proposed = toResponse(newActivity(book, input));
                Map<String, Object> payload = objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {});
                Map<String, Object> response = Map.of("status", "NEEDS_CONFIRMATION",
                        "pendingId", request.getId().toString(), "existingRecordSummary", toResponse(match),
                        "proposedRecord", proposed);
                request.awaitConfirmation(payload, asObject(response));
                return new WriteResult(HttpStatus.OK.value(), request.getResultPayload());
            }
        }

        Map<String, Object> result = saveRecord(book, input);
        request.succeed(HttpStatus.CREATED.value(), result);
        apiRequestRepository.save(request);
        return new WriteResult(HttpStatus.CREATED.value(), result);
    }

    @Transactional
    public Map<String, Object> decide(User user, long pendingId, String decision) {
        User lockedUser = lockUser(user);
        ApiRequest request = apiRequestRepository.findById(pendingId)
                .filter(item -> RECORD_ADD.equals(item.getRequestKind())
                        && lockedUser.getId().equals(item.getUser().getId())
                        && item.getPendingPayload() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "확인할 독서 기록을 찾을 수 없습니다."));

        boolean shouldAdd = "ADD".equals(decision);
        if (!request.isAwaitingConfirmation()) {
            if ((request.isSucceeded() && shouldAdd) || (request.isCancelled() && !shouldAdd)) {
                return request.getResultPayload();
            }
            throw new ActionConflictException();
        }
        if (shouldAdd) {
            ReadingRecordRequest input = objectMapper.convertValue(request.getPendingPayload(), ReadingRecordRequest.class);
            BookshelfItem book = findBook(lockedUser, input.getIsbn());
            request.succeed(HttpStatus.OK.value(), saveRecord(book, input));
        } else {
            request.cancel(Map.of("status", "CANCELLED"));
        }
        return request.getResultPayload();
    }

    @Transactional
    public ReadingRecordResponse update(User user, long recordId, ObjectNode patch) {
        User lockedUser = lockUser(user);
        ReadingActivity record = readingActivityRepository.findByIdAndBookshelfItemUser(recordId, lockedUser)
                .orElseThrow(RecordNotFoundException::new);

        LocalDate date;
        try {
            String dateText = textValue(patch, "readingDate", record.getReadingDate().toString());
            date = dateText == null ? null : LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw invalid("독서 날짜를 확인해 주세요.");
        }
        String timeBand = textValue(patch, "timeBand", record.getTimeBand());
        Integer minutes = intValue(patch, "durationMinutes", record.getDurationMinutes());
        Integer pages = intValue(patch, "pagesRead", record.getPagesRead());
        validate(date, timeBand, minutes, pages);

        if (!date.equals(record.getReadingDate()) || !Objects.equals(timeBand, record.getTimeBand())
                || !Objects.equals(minutes, record.getDurationMinutes()) || !Objects.equals(pages, record.getPagesRead())) {
            record.update(date, timeBand, minutes, pages);
        }
        return toResponse(record);
    }

    @Transactional
    public void delete(User user, long recordId) {
        User lockedUser = lockUser(user);
        readingActivityRepository.findByIdAndBookshelfItemUser(recordId, lockedUser)
                .ifPresent(readingActivityRepository::delete);
    }

    private BookshelfItem findBook(User user, String isbn) {
        return bookshelfItemRepository.findByUserAndBookIsbn13(user, isbn)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "내 책장에 없는 책입니다."));
    }

    private User lockUser(User user) {
        return userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 확인할 수 없습니다."));
    }

    private Map<String, Object> saveRecord(BookshelfItem book, ReadingRecordRequest input) {
        ReadingActivity record = readingActivityRepository.save(newActivity(book, input));
        return asObject(Map.of("status", "CREATED", "record", toResponse(record)));
    }

    private ReadingActivity newActivity(BookshelfItem book, ReadingRecordRequest input) {
        return new ReadingActivity(book, input.getReadingDate(), input.getTimeBand(),
                input.getDurationMinutes(), input.getPagesRead());
    }

    private ReadingRecordResponse toResponse(ReadingActivity activity) {
        BookshelfItem shelf = activity.getBookshelfItem();
        return new ReadingRecordResponse(
                activity.getId() == null ? null : activity.getId().toString(),
                shelf.getBook().getIsbn13(), shelf.getBook().getTitle(), activity.getReadingDate().toString(),
                activity.getTimeBand(), activity.getDurationMinutes(), activity.getPagesRead(),
                activity.getId() == null ? null : activity.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    private Map<String, Object> asObject(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }

    private byte[] hash(ReadingRecordRequest input) {
        try {
            return tokenService.hash(objectMapper.writeValueAsString(Arrays.asList(
                    input.getIsbn(), input.getReadingDate(), input.getTimeBand(), input.getDurationMinutes(),
                    input.getPagesRead(), input.getAdditionIntent())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("독서 기록 요청 확인값을 만들 수 없습니다.", e);
        }
    }

    // 빠진 필드는 기존 값을 유지하고 명시적인 null은 값을 지운다
    private String textValue(ObjectNode patch, String name, String current) {
        JsonNode value = patch.get(name);
        if (value == null) {
            return current;
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid("문자 형식의 " + name + " 값이 필요합니다.");
        }
        return value.asText();
    }

    private Integer intValue(ObjectNode patch, String name, Integer current) {
        JsonNode value = patch.get(name);
        if (value == null) {
            return current;
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("정수 형식의 " + name + " 값이 필요합니다.");
        }
        return value.intValue();
    }

    private void validate(LocalDate date, String timeBand, Integer minutes, Integer pages) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999) {
            throw invalid("독서 날짜를 확인해 주세요.");
        }
        if (timeBand != null && !"DAY".equals(timeBand) && !"NIGHT".equals(timeBand)) {
            throw invalid("독서 시간대를 확인해 주세요.");
        }
        if (minutes == null && pages == null || minutes != null && minutes < 1 || pages != null && pages < 1) {
            throw invalid("독서 시간 또는 페이지에 양수를 입력해 주세요.");
        }
    }

    private ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    @Getter
    @AllArgsConstructor
    public static class WriteResult {
        private final int statusCode;
        private final Map<String, Object> body;
    }

    public static class ActionConflictException extends RuntimeException {
    }

    public static class RecordNotFoundException extends RuntimeException {
    }
}
