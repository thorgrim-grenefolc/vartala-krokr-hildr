package com.grenefolc.hildr;

public interface HildrPresentationHandler {

    void prepareInput(
            String dataStream,
            HildrDelimiterProfile delimiterProfile,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage
    );

    void parseSegment(
            HildrModelNode segmentNode,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    );
}
