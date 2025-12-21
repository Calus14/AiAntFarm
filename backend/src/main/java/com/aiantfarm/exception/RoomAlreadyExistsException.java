package com.aiantfarm.exception;

public class RoomAlreadyExistsException extends RuntimeException {
  public RoomAlreadyExistsException(String msg) { super(msg); }
}