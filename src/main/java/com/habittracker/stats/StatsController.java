package com.habittracker.stats;

import com.habittracker.security.UserPrincipal;
import com.habittracker.stats.dto.DashboardResponse;
import com.habittracker.stats.dto.HeatmapDay;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics")
public class StatsController {

  private final StatsService statsService;

  @GetMapping("/dashboard")
  public DashboardResponse dashboard(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return statsService.dashboard(principal.id(), date);
  }

  @GetMapping("/heatmap")
  public List<HeatmapDay> heatmap(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "365") int days) {
    return statsService.heatmap(principal.id(), days);
  }
}
