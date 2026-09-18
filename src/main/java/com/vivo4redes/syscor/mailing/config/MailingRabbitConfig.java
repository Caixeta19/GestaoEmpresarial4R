package com.vivo4redes.syscor.mailing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailingRabbitConfig {

    public static final String EXCHANGE = "mailing.exchange";
    public static final String QUEUE_ENVIO_WHATSAPP = "mailing.whatsapp.enviar";
    public static final String ROUTING_KEY_ENVIO_WHATSAPP = "whatsapp.enviar";

    @Bean
    public DirectExchange mailingExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue filaEnvioWhatsapp() {
        return new Queue(QUEUE_ENVIO_WHATSAPP, true);
    }

    @Bean
    public Binding bindingEnvioWhatsapp(Queue filaEnvioWhatsapp, DirectExchange mailingExchange) {
        return BindingBuilder.bind(filaEnvioWhatsapp).to(mailingExchange).with(ROUTING_KEY_ENVIO_WHATSAPP);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}