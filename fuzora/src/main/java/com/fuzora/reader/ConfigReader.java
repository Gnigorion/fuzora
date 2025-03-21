package com.fuzora.reader;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuzora.amqp.AMQPInputConfig;
import com.fuzora.amqp.AMQPOutputConfig;
import com.fuzora.constants.AppConstants;
import com.fuzora.external.impl.ActionConfig;
import com.fuzora.external.impl.TriggerConfig;
import com.fuzora.http.HttpPollingConfig;

@Service
public class ConfigReader implements ApplicationContextAware {

	@Value("classpath:config.json")
	private Resource jsonResource;

	private JsonNode configFile;

	private JsonNode triggerConfig;
	private JsonNode filterConfig;
	private JsonNode transformerConfig;
	private JsonNode actionConfig;
	private String triggerProtocol;
	private String actionProtocol;

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigReader.class);

	public void readConfigFiles() throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		configFile = objectMapper.readTree(jsonResource.getInputStream());
		this.triggerConfig = configFile.get(AppConstants.TRIGGER);
		this.actionConfig = configFile.get(AppConstants.ACTION);
		this.transformerConfig = configFile.get(AppConstants.TRANSFORMER);
		this.filterConfig = configFile.get(AppConstants.FILTER);
		this.triggerProtocol = configFile.get(AppConstants.T_PROTOCOL).asText();
		this.actionProtocol = configFile.get(AppConstants.A_PROTOCOL).asText();

		deployTriggerConfig();
		deployActionConfig();

	}

	@Autowired(required = false)
	TriggerConfig externalTriggerConfig;

	@Autowired(required = false)
	ActionConfig externalActionConfig;

	private Map<String, Object> deployTriggerConfig() {
		Map<String, Object> retVal = switch (this.triggerProtocol) {
		case AppConstants.AMQP_INPUT -> {
			LOGGER.info("Configuring internal trigger service AMQP");
			AMQPInputConfig aic = this.applicationContext.getBean(AMQPInputConfig.class);
			yield aic.apply(this.triggerConfig);
		}
		case AppConstants.HTTP_POLLING_INPUT -> {
			LOGGER.info("Configuring internal trigger service HTTP Polling");
			HttpPollingConfig hpic = this.applicationContext.getBean(HttpPollingConfig.class);
			yield hpic.apply(this.triggerConfig);
		}
		default -> {
			LOGGER.info("Configuring external trigger service AMQP");
			yield this.externalTriggerConfig.apply(this.triggerConfig);
		}
		};

		return retVal;
	}

	private Map<String, Object> deployActionConfig() {
		Map<String, Object> retVal = switch (this.actionProtocol) {
		case AppConstants.AMQP_OUTPUT -> {
			LOGGER.info("Configuring internal action service AMQP");
			AMQPOutputConfig aoc = this.applicationContext.getBean(AMQPOutputConfig.class);
			yield aoc.apply(this.actionConfig);
		}
		default -> {
			LOGGER.info("Configuring external action service AMQP");
			yield this.externalActionConfig.apply(this.actionConfig);
		}
		};
		return retVal;
	}

	@SuppressWarnings("unchecked")
	private void deployTriggerConfigs(JsonNode triggerConfig) {
		// deploy trigger config
		Function<JsonNode, Map<String, Object>> t_conf_app = (Function<JsonNode, Map<String, Object>>) this.applicationContext
				.getBean(this.triggerProtocol);
		t_conf_app.apply(this.triggerConfig);

	}

	public JsonNode getActionConfig() {
		return actionConfig;
	}

	public JsonNode getFilterConfig() {
		return filterConfig;
	}

	public JsonNode getTransformerConfig() {
		return transformerConfig;
	}

	public JsonNode getTriggerConfig() {
		return triggerConfig;
	}

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;

	}

}
