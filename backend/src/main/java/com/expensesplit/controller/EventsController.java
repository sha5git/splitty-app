package com.expensesplit.controller;

import com.expensesplit.entity.User;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.GroupEventPublisher;
import com.expensesplit.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventsController {

    private final GroupEventPublisher groupEventPublisher;
    private final UserService userService;

    public EventsController(GroupEventPublisher groupEventPublisher, UserService userService) {
        this.groupEventPublisher = groupEventPublisher;
        this.userService = userService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal FirebaseUserPrincipal principal,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        User user = userService.getEntityByFirebaseUid(principal.getUid());
        return groupEventPublisher.subscribe(user.getId());
    }
}
