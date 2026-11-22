package com.bookuh.backend.service;

import com.bookuh.backend.dto.ReadingStatsResponse;
import com.bookuh.backend.entity.ReadingActivity;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ReadingActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReadingStatsService {

    private final ReadingActivityRepository readingActivityRepository;

    @Transactional(readOnly = true)
    public ReadingStatsResponse get(User user, String period, String timeZone) {
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(timeZone));
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 시간대를 입력해 주세요.");
        }

        boolean week = "WEEK".equals(period);
        LocalDate start = week ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);
        LocalDate end = week ? start.plusWeeks(1) : start.plusMonths(1);
        List<ReadingActivity> activities = readingActivityRepository
                .findByBookshelfItemUserAndReadingDateGreaterThanEqualAndReadingDateLessThan(user, start, end);

        return new ReadingStatsResponse(period, start, end,
                sum(activities, ReadingActivity::getDurationMinutes), sum(activities, ReadingActivity::getPagesRead),
                activities.stream().map(ReadingActivity::getReadingDate).distinct().count(),
                readingActivityRepository.countFinishedBooks(user, start, end),
                bandMinutes(activities, "DAY"), bandMinutes(activities, "NIGHT"),
                buildBuckets(activities, start, end, week));
    }

    private List<ReadingStatsResponse.Bucket> buildBuckets(
            List<ReadingActivity> activities, LocalDate start, LocalDate end, boolean week) {
        List<ReadingStatsResponse.Bucket> buckets = new ArrayList<>();
        LocalDate nextStart = start;
        while (nextStart.isBefore(end)) {
            LocalDate bucketStart = nextStart;
            LocalDate boundary = week ? bucketStart.plusDays(1)
                    : bucketStart.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            LocalDate bucketEnd = boundary.isBefore(end) ? boundary : end;
            List<ReadingActivity> inBucket = activities.stream()
                    .filter(item -> !item.getReadingDate().isBefore(bucketStart) && item.getReadingDate().isBefore(bucketEnd))
                    .toList();
            buckets.add(new ReadingStatsResponse.Bucket(bucketStart, bucketEnd,
                    sum(inBucket, ReadingActivity::getDurationMinutes)));
            nextStart = bucketEnd;
        }
        return buckets;
    }

    private Long sum(List<ReadingActivity> activities, Function<ReadingActivity, Integer> amount) {
        return activities.stream()
                .map(amount)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .reduce(Long::sum)
                .orElse(null);
    }

    private long bandMinutes(List<ReadingActivity> activities, String timeBand) {
        return activities.stream()
                .filter(item -> timeBand.equals(item.getTimeBand()))
                .map(ReadingActivity::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }
}
