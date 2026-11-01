package com.shastage.ujin.web.controller;
import com.shastage.ujin.domain.service.PythonEmulatorService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
@RestController
@RequestMapping("/api/v1/local-python")
public class PythonEmulatorController {
    private final PythonEmulatorService service;
    public PythonEmulatorController(PythonEmulatorService service){this.service=service;}
    public record Poll(UUID eventId){}
    @PostMapping("/poll") public Map<String,Object> poll(@RequestBody Poll request){return service.poll(request==null?null:request.eventId(),SecurityContextHolder.getContext().getAuthentication().getName());}
    @PostMapping("/commands/{id}/check") public Map<String,Object> check(@PathVariable UUID id){return service.check(id,SecurityContextHolder.getContext().getAuthentication().getName());}
}
