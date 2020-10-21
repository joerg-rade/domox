package domox.webapp;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import domox.SimpleModule;

@Configuration
@Import(SimpleModule.class)
@ComponentScan
public class ApplicationModule {

}
