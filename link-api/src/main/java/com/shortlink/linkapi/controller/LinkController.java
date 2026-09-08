package com.shortlink.linkapi.controller;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LinkController {

  private final LinkRepository linkRepository;

  @PostMapping("/links")
  Object createLink(@RequestBody LinkRequest linkRequest) {

    return null;
  }
}
