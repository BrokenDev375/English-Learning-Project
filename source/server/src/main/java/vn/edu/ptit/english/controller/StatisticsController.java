package vn.edu.ptit.english.controller;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.StatisticsService;

@RestController
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service) { this.service = service; }
    private ResponseEntity<?> response(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body); }
    @GetMapping("/api/me/statistics") public ResponseEntity<?> learner(@RequestParam(defaultValue = "ALL") String period,
        @AuthenticationPrincipal UserPrincipal actor) { return response(service.learner(period, actor)); }
    @GetMapping("/api/me/statistics/breakdown") public ResponseEntity<?> breakdown(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(defaultValue = "TOPIC") String groupBy, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.breakdown(period, groupBy, page, size, actor));
    }
    @GetMapping("/api/me/statistics/lessons") public ResponseEntity<?> lessons(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.lessons(page, size, actor));
    }
    @GetMapping("/api/admin/statistics") public ResponseEntity<?> admin(@RequestParam(defaultValue = "ALL") String period,
        @AuthenticationPrincipal UserPrincipal actor) { return response(service.admin(period, actor)); }
    @GetMapping("/api/admin/statistics/questions") public ResponseEntity<?> questions(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(required = false) String difficulty, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.questions(period, difficulty, page, size, actor));
    }
    @GetMapping("/api/teacher/statistics") public ResponseEntity<?> teacher(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(required = false) Long unitId, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.teacher(period, unitId, actor));
    }
    @GetMapping("/api/teacher/statistics/units") public ResponseEntity<?> teacherUnits(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.teacherUnits(page, size, actor));
    }
    @GetMapping("/api/teacher/statistics/lessons") public ResponseEntity<?> teacherLessons(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(required = false) Long unitId, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.teacherLessons(period, unitId, page, size, actor));
    }
    @GetMapping("/api/teacher/statistics/questions") public ResponseEntity<?> teacherQuestions(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(required = false) Long unitId, @RequestParam(required = false) String difficulty,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.teacherQuestions(period, unitId, difficulty, page, size, actor));
    }
    @GetMapping("/api/teacher/statistics/breakdown") public ResponseEntity<?> teacherBreakdown(@RequestParam(defaultValue = "ALL") String period,
        @RequestParam(required = false) Long unitId, @RequestParam(defaultValue = "TOPIC") String groupBy,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserPrincipal actor) {
        return response(service.teacherBreakdown(period, unitId, groupBy, page, size, actor));
    }
}
