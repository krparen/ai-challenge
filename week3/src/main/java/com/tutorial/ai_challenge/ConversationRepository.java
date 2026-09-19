package com.tutorial.ai_challenge;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

}
