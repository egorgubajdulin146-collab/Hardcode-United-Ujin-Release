package com.shastage.ujin.web.dto.response;

import com.shastage.ujin.web.dto.*;

import java.util.*;

public record PageResponse<T>(List<T> items, int page, int size, long total) {}
