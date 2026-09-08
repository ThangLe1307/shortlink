package com.shortlink.linkapi.controller;

import com.shortlink.linkapi.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LinkController {

  private final LinkRepository linkRepository;

  @GetMapping("/link/{id}")
  Object getLinkById(@PathVariable Long id) {
    return linkRepository.findById(id).orElse(null);
  }
}
