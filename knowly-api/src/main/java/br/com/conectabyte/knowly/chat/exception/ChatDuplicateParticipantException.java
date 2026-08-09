package br.com.conectabyte.knowly.chat.exception;

/**
 * REQ-10/28/33/39: the target user is already a current participant of the conversation -- reused
 * for the add-participants per-id rejection, join-request submission, and direct-join's "already a
 * member" outcome (403 CHAT_PARTICIPANT_ALREADY_MEMBER, per PLAN.md's API contract table).
 */
public class ChatDuplicateParticipantException extends RuntimeException {}
