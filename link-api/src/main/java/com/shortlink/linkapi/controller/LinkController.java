package com.shortlink.linkapi.controller;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.dto.LinkResponse;
import com.shortlink.linkapi.repository.LinkRepository;
import com.shortlink.linkapi.service.LinkService;
import com.shortlink.linkapi.service.impl.LinkServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LinkController {

  private final LinkService linkService;

  @PostMapping("/links")
  LinkResponse createLink(@RequestBody LinkRequest linkRequest) {
    return linkService.createLink(linkRequest);
  }
}
