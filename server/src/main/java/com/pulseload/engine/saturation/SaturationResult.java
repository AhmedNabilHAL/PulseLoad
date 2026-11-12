package com.pulseload.engine.saturation;

import java.util.List;

public record SaturationResult(boolean saturated, List<SaturationReason> reasons) {
}
