package domox.webapp.application;

import domox.SimpleModule;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SimpleModule.class)
@ComponentScan
public class ApplicationModule {

}
