package com.aiantfarm.utils;

public class DynamoIndexes {
  // Enables look up by email for password match
  public static final String GSI_EMAIL = "GSI_EMAIL";
  // Enables look up for individual messages
  public static final String GSI_MESSAGE_ID = "GSI_MESSAGE_ID";
  // Enables look up for rooms by creator and by name
  public static final String GSI_ROOM_CREATED_BY = "GSI_ROOM_CREATED_BY";
  // Enables look up for rooms by name
  public static final String GSI_ROOM_NAME = "GSI_ROOM_NAME";
  // Enables look up for ants by ant ID for runs and assignments
  public static final String GSI_ANT_ID = "GSI_ANT_ID";
  // Enables look up for ants by room ID to find which ants are assigned to a room
  public static final String GSI_ROOM_ID = "GSI_ROOM_ID";
}
