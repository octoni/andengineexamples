package org.anddev.andengine.examples.game.pong;

import java.io.IOException;

import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.engine.LimitedFPSEngine;
import org.anddev.andengine.engine.camera.Camera;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.EngineOptions.ScreenOrientation;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.primitive.Line;
import org.anddev.andengine.entity.primitive.Rectangle;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.scene.Scene.IOnSceneTouchListener;
import org.anddev.andengine.entity.text.ChangeableText;
import org.anddev.andengine.entity.util.FPSLogger;
import org.anddev.andengine.examples.game.pong.PongServerConnection.IPongServerConnectionListener;
import org.anddev.andengine.examples.game.pong.adt.MovePaddleClientMessage;
import org.anddev.andengine.examples.game.pong.util.constants.PongConstants;
import org.anddev.andengine.extension.multiplayer.protocol.adt.message.client.BaseClientMessage;
import org.anddev.andengine.extension.multiplayer.protocol.adt.message.client.connection.ConnectionPingClientMessage;
import org.anddev.andengine.extension.multiplayer.protocol.adt.message.server.BaseServerMessage;
import org.anddev.andengine.extension.multiplayer.protocol.client.BaseServerConnectionListener;
import org.anddev.andengine.extension.multiplayer.protocol.server.BaseClientConnectionListener;
import org.anddev.andengine.extension.multiplayer.protocol.shared.BaseConnection;
import org.anddev.andengine.extension.multiplayer.protocol.util.IPUtils;
import org.anddev.andengine.input.touch.TouchEvent;
import org.anddev.andengine.opengl.font.Font;
import org.anddev.andengine.opengl.font.FontFactory;
import org.anddev.andengine.opengl.texture.Texture;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.ui.activity.BaseGameActivity;
import org.anddev.andengine.util.Debug;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

/**
 * @author Nicolas Gramlich
 * @since 19:36:45 - 28.02.2011
 */
public class PongGameActivity extends BaseGameActivity implements PongConstants, IPongServerConnectionListener, IOnSceneTouchListener {
	// ===========================================================
	// Constants
	// ===========================================================

	private static final String LOCALHOST_IP = "127.0.0.1";

	private static final int CAMERA_WIDTH = GAME_WIDTH;
	private static final int CAMERA_HEIGHT = GAME_HEIGHT;

	private static final int DIALOG_CHOOSE_SERVER_OR_CLIENT_ID = 0;
	private static final int DIALOG_ENTER_SERVER_IP_ID = DIALOG_CHOOSE_SERVER_OR_CLIENT_ID + 1;
	private static final int DIALOG_SHOW_SERVER_IP_ID = DIALOG_ENTER_SERVER_IP_ID + 1;

	private static final int PADDLEID_NOT_SET = -1;

	private static final int MENU_PING = Menu.FIRST;

	// ===========================================================
	// Fields
	// ===========================================================

	private Camera mCamera;

	private String mServerIP = LOCALHOST_IP;

	private int mPaddleID = PADDLEID_NOT_SET;

	private PongServer mServer;
	private PongServerConnection mServerConnection;

	private Rectangle mBall;
	private final SparseArray<Rectangle> mPaddleMap = new SparseArray<Rectangle>();
	private final SparseArray<ChangeableText> mScoreChangeableTextMap = new SparseArray<ChangeableText>();

	private Texture mScoreFontTexture;
	private Font mScoreFont;

	// ===========================================================
	// Constructors
	// ===========================================================

	@Override
	public Engine onLoadEngine() {
		this.showDialog(DIALOG_CHOOSE_SERVER_OR_CLIENT_ID);

		this.mCamera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);
		this.mCamera.setCenter(0,0);

		final EngineOptions engineOptions = new EngineOptions(true, ScreenOrientation.LANDSCAPE, new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), this.mCamera);
		engineOptions.getTouchOptions().setRunOnUpdateThread(true);
		return new LimitedFPSEngine(engineOptions, FPS);
	}

	@Override
	public void onLoadResources() {
		this.mScoreFontTexture = new Texture(256, 256, TextureOptions.BILINEAR_PREMULTIPLYALPHA);

		FontFactory.setAssetBasePath("font/");
		this.mScoreFont = FontFactory.createFromAsset(this.mScoreFontTexture, this, "LCD.ttf", 32, true, Color.WHITE);

		this.mEngine.getTextureManager().loadTexture(this.mScoreFontTexture);
		this.mEngine.getFontManager().loadFont(this.mScoreFont);
	}

	@Override
	public Scene onLoadScene() {
		this.mEngine.registerUpdateHandler(new FPSLogger());

		final Scene scene = new Scene(1);

		/* Ball */
		this.mBall = new Rectangle(0, 0, BALL_WIDTH, BALL_HEIGHT);
		scene.attachChild(this.mBall);

		/* Walls */
		scene.attachChild(new Line(-GAME_WIDTH_HALF + 1, -GAME_HEIGHT_HALF, -GAME_WIDTH_HALF + 1, GAME_HEIGHT_HALF)); // Left
		scene.attachChild(new Line(GAME_WIDTH_HALF, -GAME_HEIGHT_HALF, GAME_WIDTH_HALF, GAME_HEIGHT_HALF)); // Right
		scene.attachChild(new Line(-GAME_WIDTH_HALF, -GAME_HEIGHT_HALF + 1, GAME_WIDTH_HALF , -GAME_HEIGHT_HALF + 1)); // Top
		scene.attachChild(new Line(-GAME_WIDTH_HALF, GAME_HEIGHT_HALF, GAME_WIDTH_HALF, GAME_HEIGHT_HALF)); // Bottom

		scene.attachChild(new Line(0, -GAME_HEIGHT_HALF, 0, GAME_HEIGHT_HALF)); // Middle

		/* Paddles */
		final Rectangle paddleLeft = new Rectangle(0, 0, PADDLE_WIDTH, PADDLE_HEIGHT);
		final Rectangle paddleRight = new Rectangle(0, 0, PADDLE_WIDTH, PADDLE_HEIGHT);
		this.mPaddleMap.put(PADDLE_LEFT.getOwnerID(), paddleLeft);
		this.mPaddleMap.put(PADDLE_RIGHT.getOwnerID(), paddleRight);

		scene.attachChild(paddleLeft);
		scene.attachChild(paddleRight);

		/* Scores */
		final ChangeableText scoreLeft = new ChangeableText(0, -GAME_HEIGHT_HALF + SCORE_PADDING, this.mScoreFont, "0", 2);
		scoreLeft.setPosition(-scoreLeft.getWidth() - SCORE_PADDING, scoreLeft.getY());
		final ChangeableText scoreRight = new ChangeableText(SCORE_PADDING, -GAME_HEIGHT_HALF + SCORE_PADDING, this.mScoreFont, "0", 2);
		this.mScoreChangeableTextMap.put(PADDLE_LEFT.getOwnerID(), scoreLeft);
		this.mScoreChangeableTextMap.put(PADDLE_RIGHT.getOwnerID(), scoreRight);

		scene.attachChild(scoreLeft);
		scene.attachChild(scoreRight);

		scene.setOnSceneTouchListener(this);

		return scene;
	}

	@Override
	public void onLoadComplete() {

	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public boolean onCreateOptionsMenu(final Menu pMenu) {
		pMenu.add(Menu.NONE, MENU_PING, Menu.NONE, "Ping Server");
		return super.onCreateOptionsMenu(pMenu);
	}

	@Override
	public boolean onMenuItemSelected(final int pFeatureId, final MenuItem pItem) {
		switch(pItem.getItemId()) {
			case MENU_PING:
				try {
					this.mServerConnection.sendClientMessage(new ConnectionPingClientMessage());
				} catch (final IOException e) {
					Debug.e(e);
				}
				return true;
			default:
				return super.onMenuItemSelected(pFeatureId, pItem);
		}
	}

	@Override
	protected Dialog onCreateDialog(final int pID) {
		switch(pID) {
			case DIALOG_SHOW_SERVER_IP_ID:
				return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle("Your Server-IP ...")
				.setCancelable(false)
				.setMessage("The IP of your Server is:\n" + IPUtils.getIPAddress(this))
				.setPositiveButton(android.R.string.ok, null)
				.create();
			case DIALOG_ENTER_SERVER_IP_ID:
				final EditText ipEditText = new EditText(this);
				return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle("Enter Server-IP ...")
				.setCancelable(false)
				.setView(ipEditText)
				.setPositiveButton("Connect", new OnClickListener() {
					@Override
					public void onClick(final DialogInterface pDialog, final int pWhich) {
						PongGameActivity.this.mServerIP = ipEditText.getText().toString();
						PongGameActivity.this.initClient();
					}
				})
				.setNegativeButton(android.R.string.cancel, new OnClickListener() {
					@Override
					public void onClick(final DialogInterface pDialog, final int pWhich) {
						PongGameActivity.this.finish();
					}
				})
				.create();
			case DIALOG_CHOOSE_SERVER_OR_CLIENT_ID:
				return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle("Be Server or Client ...")
				.setCancelable(false)
				.setPositiveButton("Client", new OnClickListener() {
					@Override
					public void onClick(final DialogInterface pDialog, final int pWhich) {
						PongGameActivity.this.showDialog(DIALOG_ENTER_SERVER_IP_ID);
					}
				})
				.setNeutralButton("Server", new OnClickListener() {
					@Override
					public void onClick(final DialogInterface pDialog, final int pWhich) {
						PongGameActivity.this.initServerAndClient();
						PongGameActivity.this.showDialog(DIALOG_SHOW_SERVER_IP_ID);
					}
				})
				.create();
			default:
				return super.onCreateDialog(pID);
		}
	}

	@Override
	protected void onDestroy() {
		if(this.mServer != null) {
			this.mServer.interrupt();
		}

		if(this.mServerConnection != null) {
			this.mServerConnection.interrupt();
		}

		super.onDestroy();
	}

	@Override
	public boolean onSceneTouchEvent(final Scene pScene, final TouchEvent pSceneTouchEvent) {
		if(this.mPaddleID != PADDLEID_NOT_SET) {
			try {
				// TODO Pooling
				this.mServerConnection.sendClientMessage(new MovePaddleClientMessage(this.mPaddleID, pSceneTouchEvent.getY()));
			} catch (final IOException e) {
				Debug.e(e);
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void updateScore(final int pPaddleID, final int pPoints) {
		final ChangeableText scoreChangeableText = this.mScoreChangeableTextMap.get(pPaddleID);
		scoreChangeableText.setText(String.valueOf(pPoints));
		
		/* Adjust position of left Score, so that it doesn't overlap the middle line. */
		if(pPaddleID == PADDLE_LEFT.getOwnerID()) {
			scoreChangeableText.setPosition(-scoreChangeableText.getWidth() - SCORE_PADDING, scoreChangeableText.getY());
		}
	}

	@Override
	public void setPaddleID(final int pPaddleID) {
		this.mPaddleID = pPaddleID;
	}

	@Override
	public void updatePaddle(final int pPaddleID, final float pX, final float pY) {
		this.mPaddleMap.get(pPaddleID).setPosition(pX, pY);
	}

	@Override
	public void updateBall(final float pX, final float pY) {
		this.mBall.setPosition(pX, pY);
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void initServerAndClient() {
		PongGameActivity.this.initServer();

		/* Wait some time after the server has been started, so it actually can start up. */
		try {
			Thread.sleep(500);
		} catch (final Throwable t) {
			Debug.e("Error", t);
		}

		PongGameActivity.this.initClient();
	}

	private void initServer() {
		this.mServer = new PongServer(new ExampleClientConnectionListener());

		this.mServer.start();

		this.mEngine.registerUpdateHandler(this.mServer);
	}

	private void initClient() {
		try {
			this.mServerConnection = new PongServerConnection(this.mServerIP, new ExampleServerConnectionListener(), this);

			this.mServerConnection.start();
		} catch (final Throwable t) {
			Debug.e("Error", t);
		}
	}

	private void toast(final String pMessage) {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(PongGameActivity.this, pMessage, Toast.LENGTH_SHORT).show();
			}
		});
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class ExampleServerConnectionListener extends BaseServerConnectionListener {
		@Override
		protected void onConnected(final BaseConnection<BaseServerMessage> pConnection) {
			PongGameActivity.this.toast("CLIENT: Connected to server.");
		}

		@Override
		protected void onDisconnected(final BaseConnection<BaseServerMessage> pConnection) {
			PongGameActivity.this.toast("CLIENT: Disconnected from Server...");
			PongGameActivity.this.finish();
		}
	}

	private class ExampleClientConnectionListener extends BaseClientConnectionListener {
		@Override
		protected void onConnected(final BaseConnection<BaseClientMessage> pConnection) {
			PongGameActivity.this.toast("SERVER: Client connected: " + pConnection.getSocket().getInetAddress().getHostAddress());
		}

		@Override
		protected void onDisconnected(final BaseConnection<BaseClientMessage> pConnection) {
			PongGameActivity.this.toast("SERVER: Client disconnected: " + pConnection.getSocket().getInetAddress().getHostAddress());
		}
	}
}