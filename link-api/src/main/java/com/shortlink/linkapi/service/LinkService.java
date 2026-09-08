package com.shortlink.linkapi.service;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.dto.LinkResponse;

public interface LinkService {

    LinkResponse createLink(LinkRequest linkRequest);

}
