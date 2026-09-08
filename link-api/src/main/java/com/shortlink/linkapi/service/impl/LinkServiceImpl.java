package com.shortlink.linkapi.service.impl;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.dto.LinkResponse;
import com.shortlink.linkapi.repository.LinkRepository;
import com.shortlink.linkapi.service.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkServiceImpl implements LinkService {

    private final LinkRepository linkRepository;

    @Override
    public LinkResponse createLink(LinkRequest linkRequest) {
        return null;
    }
}
