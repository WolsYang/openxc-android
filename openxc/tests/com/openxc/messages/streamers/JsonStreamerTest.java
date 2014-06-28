package com.openxc.messages.streamers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.openxc.messages.NamedVehicleMessage;
import com.openxc.messages.VehicleMessage;

@Config(emulateSdk = 18, manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class JsonStreamerTest {
    JsonStreamer streamer;

    @Before
    public void setup() {
        streamer = new JsonStreamer();
    }

    @Test
    public void jsonContainsJson() {
        assertTrue(streamer.containsJson("{\"name\": \"foo\"}\u0000"));
    }

    @Test
    public void paritalJsonDoesntContainJson() {
        assertFalse(streamer.containsJson("{\"name\": \"foo\"}\u0000\u0001\u0002"));
    }

    @Test
    public void notJsonDoesntContainJson() {
        assertFalse(streamer.containsJson("\u0000\u0001\u0002"));
    }

    @Test
    public void emptyHasNoMessages() {
        assertThat(streamer.parseNextMessage(), nullValue());
    }

    @Test
    public void receiveLessThanFullBufferDoesntGrabAll() {
        // When we receive bytes, we have a buffer with bytes but only part of
        // it may be valid. The streamer must only try and use the valid part of
        // the buffer.
        byte[] bytes = new String(
                "{\"name\": \"bar\"}\u0000{\"name\": \"bing\"}\u0000").getBytes();
        // Tell the JsonStreamer that the buffer is only valid up to the end of
        // the first message.
        streamer.receive(bytes, 16);

        assertThat(streamer.parseNextMessage(), notNullValue());
        // If we can parse both, it's broken.
        assertThat(streamer.parseNextMessage(), nullValue());
    }

    @Test
    public void clearsDelimitersInFrontOfMessage() {
        byte[] bytes = new String("\u0000\u0000{\"foo\": \"bar\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
    }

    @Test
    public void readValidUnrecognized() {
        byte[] bytes = new String("{\"foo\": \"bar\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
    }

    @Test
    public void readingGenericThenSpecific() {
        byte[] bytes = new String("{\"foo\": \"bar\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        bytes = new String("{\"name\": \"foo\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), notNullValue());
    }

    @Test
    public void readLinesOne() {
        byte[] bytes = new String("{\"name\": \"foo\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        VehicleMessage message = streamer.parseNextMessage();
        assertThat(message, notNullValue());
        assertThat(message, instanceOf(NamedVehicleMessage.class));
        NamedVehicleMessage namedMessage = (NamedVehicleMessage) message;
        assertThat(namedMessage.getName(), equalTo("foo"));

        assertThat(streamer.parseNextMessage(), nullValue());
    }

    @Test
    public void readLinesTwo() {
        byte[] bytes = new String("{\"name\": \"foo\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        bytes = new String("{\"name\": \"miracle\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), nullValue());
    }

    @Test
    public void leavePartial() {
        byte[] bytes = new String("{\"name\": \"foo\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        bytes = new String("{\"name\": \"mira").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), nullValue());
    }

    @Test
    public void completePartial() {
        byte[] bytes = new String("{\"name\": \"foo\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        bytes = new String("{\"name\": \"mira").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), nullValue());

        bytes = new String("cle\"}\u0000").getBytes();
        streamer.receive(bytes, bytes.length);

        assertThat(streamer.parseNextMessage(), notNullValue());
        assertThat(streamer.parseNextMessage(), nullValue());
    }
}
