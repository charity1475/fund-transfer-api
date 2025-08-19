package co.nbc.fundtransferapi.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.endpoint.dsl.FreemarkerEndpointBuilderFactory.FreemarkerEndpointBuilder;
import org.apache.camel.builder.endpoint.dsl.JsonValidatorEndpointBuilderFactory.JsonValidatorEndpointBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.freemarker;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.jsonValidator;

@Component
public class TransactionsRoute extends RouteBuilder {
  private static final JsonValidatorEndpointBuilder jsonSchemaValidator = jsonValidator("classpath:{{validation-schema.uri}}");
  private static final FreemarkerEndpointBuilder freemarkerResponseTemplate = freemarker("{{freemarker.error-template.uri}}")
    .encoding("UTF-8").contentCache(true).allowTemplateFromHeader(false);
  private static final String processTransactionRoute = "direct:processTransaction";
  private static final String errorRoute = "direct:error";
  private final int port = Integer.parseInt(System.getProperty("server.port", "8080"));

  @Bean
  public PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean(name = "PROPAGATION_REQUIRED")
  public SpringTransactionPolicy propagationRequired(PlatformTransactionManager transactionManager) {
    SpringTransactionPolicy propagationRequired = new SpringTransactionPolicy();
    propagationRequired.setTransactionManager(transactionManager);
    propagationRequired.setPropagationBehaviorName("PROPAGATION_REQUIRED");
    return propagationRequired;
  }


  @Override
  public void configure() {
    errorHandler(deadLetterChannel(errorRoute).maximumRedeliveries(0));
    onException(Exception.class).handled(true).to(errorRoute);

    from(errorRoute)
      .routeId("errorHandlerRoute")
      .setHeader("status", simple("601"))
      .setHeader("message", simple("${exception.message}"))
      .to(freemarkerResponseTemplate).log(ERROR, "Error: ${body}").removeHeaders("*")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
      .setHeader("Content-Type", constant("application/json"));

    restConfiguration().component("servlet")
      .host("0.0.0.0").port(port).dataFormatProperty("prettyPrint", "true");

    rest("/v1/transactions")
      .put()
      .to(processTransactionRoute);

    from(processTransactionRoute)
      .routeId("processTransactionRoute").log(INFO, "Validating request - ${exchangeId}")
      .to(jsonSchemaValidator).unmarshal().json().log(INFO, "Inserting new transaction: ${body}")
      .transacted("PROPAGATION_REQUIRED")
      .doTry()
          .to("sql:INSERT INTO transactions (service, name, amount, account, reference) VALUES (:#service, :#name, :#amount, :#account, :#reference)")
          .setHeader("status", constant("600"))
          .setHeader("message", constant("Transaction processed successfully."))
          .setVariable("httpStatusCode", constant(200))
      .doCatch(SQLIntegrityConstraintViolationException.class, DuplicateKeyException.class)
          .setHeader("status", constant("601"))
          .setHeader("message", constant("Duplicate reference received, try with another one."))
          .setVariable("httpStatusCode", constant(500))
      .doCatch(SQLException.class)
          .setHeader("status", constant("601"))
          .setHeader("message", simple("${exception.message}"))
          .setVariable("httpStatusCode", constant(500))
      .endDoTry()
      .end()
      .to(freemarkerResponseTemplate).removeHeaders("*")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, simple("${variable.httpStatusCode}"))
      .setHeader("Content-Type", constant("application/json"));
  }
}
