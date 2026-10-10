package com.payroll.web.notes;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NotesController {

    private final NoteService service;

    public NotesController(NoteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NoteResponse> addNote(@Valid @RequestBody NoteRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(service.addNote(request, authentication.getName()));
    }

    // Employee-scoped rather than single-date-scoped (unlike 6C/6D's GET pattern),
    // since Super Admin wants an employee's full note history at a glance.
    @GetMapping("/{companyId}/{employeeCode}")
    public ResponseEntity<List<NoteResponse>> getForEmployee(
            @PathVariable("companyId") Long companyId,
            @PathVariable("employeeCode") String employeeCode) {
        return ResponseEntity.ok(service.findByEmployee(companyId, employeeCode));
    }
}
