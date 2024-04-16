package domox.webapp.application;

import domox.DomainModule;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(value = DomainModule.class)
@ComponentScan
public class ApplicationModule {

}
