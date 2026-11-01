package com.shastage.ujin.web.dto.response;

import java.util.*;

public record EventReceiptResponse(UUID eventId, String status, List<UUID> runIds) {}
