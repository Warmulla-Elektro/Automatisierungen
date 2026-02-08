package de.warmulla_elektro.hourtables;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.comroid.api.func.ext.Context;
import org.comroid.api.func.util.RootContextSource;

public class RootContextProvider implements RootContextSource {
    @Override
    public Context getRootContext() {
        return new Context.Base() {{
            myMembers.add(new ObjectMapper(new JsonFactoryBuilder() {{
                enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
            }}.build()) {
                {
                    registerModule(new JavaTimeModule());
                }
            });
        }};
    }
}
