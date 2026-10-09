package com.workshop.controller;

import com.workshop.dto.ContactRequest;
import com.workshop.dto.ContactResponse;
import com.workshop.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@Tag(name = "Contact Us", description = "Public contact form")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit the Contact Us form (public)")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Submission saved with status NEW"))
    public ContactResponse submit(@Valid @RequestBody ContactRequest request) {
        return contactService.submit(request);
    }
}
