package com.grenefolc.hildr;

import com.boomi.connector.api.AtomContext;
import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.Browser;
import com.boomi.connector.api.Operation;
import com.boomi.connector.api.OperationContext;
import com.boomi.connector.util.BaseConnector;

public class HildrConnector extends BaseConnector {

    @Override
    public void initialize(AtomContext context) {
        // no-op
    }

    @Override
    public Browser createBrowser(BrowseContext context) {
        throw new UnsupportedOperationException("Browse not supported");
    }

    @Override
    public Operation createOperation(OperationContext context) {
        return new HildrOperation(context);
    }
}
