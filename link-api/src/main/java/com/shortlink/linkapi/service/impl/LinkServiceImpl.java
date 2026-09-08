package com.shortlink.linkapi.service.impl;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.dto.LinkResponse;
import com.shortlink.linkapi.entity.LinkEntity;
import com.shortlink.linkapi.repository.LinkRepository;
import com.shortlink.linkapi.service.LinkService;
import com.shortlink.linkapi.validation.LinkValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import static com.shortlink.linkapi.constant.ReservedCode.RESERVED_CODE_LIST;

@Service
@RequiredArgsConstructor
public class LinkServiceImpl implements LinkService {

    private final LinkRepository linkRepository;
    private final LinkValidator linkValidator;

    @Override
    public LinkResponse createLink(LinkRequest linkRequest) {

        linkValidator.validateRequest(linkRequest);

        LinkEntity linkEntity = LinkEntity.builder()
                .code(linkRequest.getCustomCode())
                .createdAt(OffsetDateTime.now())
                .expiresAt(linkRequest.getExpiresAt())
                .targetUrl(linkRequest.getTargetUrl()).build();

         linkRepository.save(linkEntity);

        return new LinkResponse(
                linkEntity.getCode(),
                null,
                linkEntity.getTargetUrl(),
                null,
                null,
                null
        );
    }
}
