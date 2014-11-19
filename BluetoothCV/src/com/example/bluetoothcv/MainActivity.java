package com.example.bluetoothcv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends Activity implements CvCameraViewListener2{

	private static final String    TAG = "BlueCV";
	private static final int REQUEST_ENABLE_BT = 1;
	private static final String TAG_OUT = "CamerFrameOut";


	private TextView text;
	private Button btnSend;

	private BluetoothAdapter myBluetoothAdapter;
	private Set<BluetoothDevice> pairedDevices;
	private ListView myListView;
	private ArrayAdapter<String> BTArrayAdapter;

	private OutputStream mmOutputStream;	
	private InputStream mmInputStream;
	private BluetoothDevice mmDevice;
	private BluetoothSocket mmSocket;
	private String chosenOne; // Device Name that has been chosen

	private volatile boolean stopWorker;
	private Thread workerThread;
	private byte[] readBuffer;
	private int readBufferPosition;


	private Mat mRgba;
	private Mat HSV ;
	private Mat blob;
//	private Mat mRgbaT;
	private Rect boundRect;

	private CameraBridgeViewBase   mOpenCvCameraView;

	private ViewSwitcher viewSwitcher;

	private EditText editTextSend;
	
	private double mCamWidth;
	private double mCamHeight;


	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS:{
				Log.i(TAG, "OpenCV loaded successfully");
				mOpenCvCameraView.enableView();
			} break;

			default:{
				super.onManagerConnected(status);
			} break;
			}
		}
	};






	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		viewSwitcher = (ViewSwitcher) findViewById(R.id.viewSwitcher1);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camView);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);
		

		text = (TextView) findViewById(R.id.text);

		mmDevice = null;
		chosenOne = null;

		// take an instance of BluetoothAdapter - Bluetooth radio
		myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(myBluetoothAdapter == null) {
			//			onBtn.setEnabled(false);
			//			offBtn.setEnabled(false);
			//			listBtn.setEnabled(false);
			//			findBtn.setEnabled(false);
			text.setText("Status: not supported");

			Toast.makeText(getApplicationContext(),"Your device does not support Bluetooth",
					Toast.LENGTH_LONG).show();
		} else {
			editTextSend = (EditText) findViewById(R.id.editTextSend);
			

			btnSend = (Button)findViewById(R.id.btnSend);
			btnSend.setOnClickListener(new OnClickListener(){
				public void onClick (View v) {
					sendMsg (editTextSend.getText().toString() + "\n");
				}
			});


			myListView = (ListView)findViewById(R.id.listView1);

			// create the arrayAdapter that contains the BTDevices, and set it to the ListView
			BTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
			myListView.setAdapter(BTArrayAdapter);

			myListView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick (AdapterView<?> parent, View view,
						int position, long id) {
					// Set mmDevice

					String adapter = ((TextView) view).getText().toString();
					mmDevice = null;
					text.setText(adapter);
					chosenOne = adapter;
					myBluetoothAdapter.cancelDiscovery();

					BTArrayAdapter.clear();
					myBluetoothAdapter.startDiscovery();
					text.setText ( "Start Discovery Again");
					registerReceiver(bReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));	

				}
			});

		}



	}

	@Override
	public void onResume(){
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(bReceiver);
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {

			viewSwitcher.showNext();

			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);
		HSV = new Mat();
		blob = new Mat();
//		mRgbaT = new Mat();
		
//		mCamWidth = mOpenCvCameraView.getWidth();
//		mCamHeight = mOpenCvCameraView.getHeight();

	}

	@Override
	public void onCameraViewStopped() {
		Log.i(TAG_OUT, "camera stpooed");
		mRgba.release();
		HSV.release();
		blob.release();
		//		mRgbaT.release();

	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

		System.gc();

		mRgba = inputFrame.rgba();
//		//		 mRgbaT = mRgba.t();
//		Core.flip(mRgba.t(), mRgbaT, 1);
//		Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());
//
//
//		Imgproc.cvtColor(mRgbaT, HSV, Imgproc.COLOR_RGB2HSV);
		
		Imgproc.cvtColor(mRgba, HSV, Imgproc.COLOR_RGB2HSV);

		// orange color detect in HSV
		Core.inRange(HSV, new Scalar(5, 150, 150), new Scalar(15, 255, 255), blob);

//		mRgbaT.copyTo(mRgba);

		List<MatOfPoint> contours = new ArrayList<MatOfPoint>(); //vector<vector<Point> > contours;
		Mat hierarchy = new Mat();


		Imgproc.findContours(blob, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		hierarchy.release();

		int k = getBiggestContourIndex(contours);


		boundRect = setContourRect(contours, k);
		Point center = new Point();
		getCenterPoint(boundRect.tl(), boundRect.br(), center);
		Core.rectangle(mRgba, boundRect.tl(), boundRect.br(), new Scalar(255, 255, 0), 2, 8, 0 );

		Double lx = boundRect.tl().x;
		Double ty = boundRect.tl().y;

		Double rx = boundRect.br().x;
		Double by = boundRect.br().y;

		Core.putText(mRgba, lx.toString() + "," + ty.toString(), boundRect.tl(), 0/*font*/, 1, new Scalar(255, 0, 0, 255), 3);

		// left bound 50 to 100 right bound 700 750
		//
		// high y 0   low y 600
		
		Size a = mRgba.size();
		
		mCamWidth = a.width;
		mCamHeight = a.height;

		
		
		double xLeftLimit = mCamWidth /10;
		double yTopLimit = mCamHeight /10;
		double xRightLimit = mCamWidth - (mCamWidth /10);
		double yBotLimit = mCamWidth - (mCamHeight /10);
		
		if(mmDevice != null){

			String msg = null;
			if(lx < xLeftLimit && rx < xRightLimit && by < yBotLimit){
				//turn left

				Log.i("CAM", "left");
				
				msg = "l";
			} else if(lx > xLeftLimit && rx > xRightLimit && by < yBotLimit){
				// turn right
				
				Log.i("CAM", "right");
				msg = "r";
			} else if(by > yBotLimit){
				
				Log.i("CAM", "stop");
				msg = "s";
				
			} else if(lx > xLeftLimit && rx < xRightLimit && by < yBotLimit){
			
				// move forward

				Log.i("CAM", "forward");
				msg = "f";
			} else {
				// stop

				Log.i("CAM", "stop");
				msg = "s";
			}
			
			try {
				mmOutputStream.write(msg.getBytes());
			} catch (IOException e) {
				
				e.printStackTrace();
			}
			
		}
		
		

		return mRgba;
		//		return inputFrame.rgba();


	}

	public void on(View view){
		if (!myBluetoothAdapter.isEnabled()) {
			Intent turnOnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(turnOnIntent, REQUEST_ENABLE_BT);

			Toast.makeText(getApplicationContext(),"Bluetooth turned on" ,
					Toast.LENGTH_LONG).show();
		}
		else{
			Toast.makeText(getApplicationContext(),"Bluetooth is already on",
					Toast.LENGTH_LONG).show();
		}
	}

	public void off(View view){
		myBluetoothAdapter.disable();
		text.setText("Status: Disconnected");

		Toast.makeText(getApplicationContext(),"Bluetooth turned off",
				Toast.LENGTH_LONG).show();
	}

	public void find(View view) {
		if (myBluetoothAdapter.isDiscovering()) {
			// the button is pressed when it discovers, so cancel the discovery
			myBluetoothAdapter.cancelDiscovery();
			text.setText ( "Discovery Cancelled");

		}
		else {
			BTArrayAdapter.clear();
			myBluetoothAdapter.startDiscovery();
			text.setText ( "Start Discovery");
			registerReceiver(bReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));	
		}    
	}

	public static void getCenterPoint(Point tl, Point br, Point dst){
		dst.x = (tl.x + br.x)/2;
		dst.y = (tl.y + br.y)/2;
	}

	public static int getBiggestContourIndex(List<MatOfPoint> contours){
		double maxArea = 0;
		Iterator<MatOfPoint> each = contours.iterator();
		int j = 0;
		int k = -1;
		while (each.hasNext())
		{
			MatOfPoint wrapper = each.next();
			double area = Imgproc.contourArea(wrapper);
			if (area > maxArea){
				maxArea = area;
				k = j;
			}
			j++;
		}
		return k;
	}

	public static Rect setContourRect(List<MatOfPoint> contours,int k){
		Rect boundRect = new Rect();
		Iterator<MatOfPoint> each = contours.iterator();
		int j = 0;
		while (each.hasNext()){
			MatOfPoint wrapper = each.next();
			if (j==k){
				return Imgproc.boundingRect( wrapper );
			}
			j++;
		}
		return boundRect;
	}

	void sendMsg (String msg) {
		if (mmDevice == null)
			text.setText("Connect first");
		else {    	   
			try {
				mmOutputStream.write(msg.getBytes());
			} catch (IOException ex) { }
			text.setText("Sent " + msg);
		}   

	}

	final BroadcastReceiver bReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			String name;
			Integer index;
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// add the name and the MAC address of the object to the arrayAdapter
				BTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
				BTArrayAdapter.notifyDataSetChanged();

				if ((chosenOne != null) && (mmDevice == null)) {	        		 
					index = chosenOne.indexOf("\n");
					if (index > -1) {	        			 
						name = chosenOne.substring(0,index);
						if (name.compareTo(device.getName()) == 0) {
							mmDevice = device;
							text.setText ( "device set");
							myBluetoothAdapter.cancelDiscovery();
							try {
								openBT();
							}
							catch (IOException ex) { }
							BTArrayAdapter.clear();
							BTArrayAdapter.notifyDataSetChanged();
						}   
						else {
							text.setText ( "Bad " + device.getName() + " not " + name + ".");
						}
					} 
					else
						text.setText ( "index == 0 for " + chosenOne );
				}	 

			}
		}
	};
	void openBT() throws IOException
	{
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
		mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
		mmSocket.connect();
		mmOutputStream = mmSocket.getOutputStream();
		mmInputStream  = mmSocket.getInputStream();

		//String msg = myTextbox.getText().toString();
		//msg += "\n";
//		String msg = "Hello from Android\n";
		String msg = "c";
		mmOutputStream.write(msg.getBytes());
		text.setText("Data Sent");

		beginListenForData();       
		// text.setText("Bluetooth Opened");
	}

	void beginListenForData()
	{
		final Handler handler = new Handler(); 
		final byte delimiter = 10; //This is the ASCII code for a newline character

		stopWorker = false;
		readBufferPosition = 0;
		readBuffer = new byte[1024];
		workerThread = new Thread(new Runnable()
		{
			public void run()
			{                
				while(!Thread.currentThread().isInterrupted() && !stopWorker)
				{
					try 
					{
						int bytesAvailable = mmInputStream.available();                        
						if(bytesAvailable > 0)
						{
							byte[] packetBytes = new byte[bytesAvailable];
							mmInputStream.read(packetBytes);
							for(int i=0;i<bytesAvailable;i++)
							{
								byte b = packetBytes[i];
								if(b == delimiter)
								{
									byte[] encodedBytes = new byte[readBufferPosition];
									System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
									final String data = new String(encodedBytes, "US-ASCII");
									readBufferPosition = 0;

									handler.post(new Runnable()
									{
										public void run()
										{
											text.setText(data);
										}
									});
								}
								else
								{
									readBuffer[readBufferPosition++] = b;
								}
							}
						}
					} 
					catch (IOException ex) 
					{
						stopWorker = true;
					}
				}
			}
		});

		workerThread.start();
	}
}
