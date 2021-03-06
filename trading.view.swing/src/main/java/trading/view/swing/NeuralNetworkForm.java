package trading.view.swing;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.JFormattedTextField;
import javax.swing.SwingConstants;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JLabel;

import java.awt.Font;

import javax.swing.SpringLayout;
import javax.swing.Box;
import javax.swing.border.LineBorder;

import org.springframework.context.support.GenericXmlApplicationContext;

import trading.app.neural.NeuralContext;

import java.awt.Color;
import javax.swing.JButton;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class NeuralNetworkForm extends JFrame {
	// Spring application context
	static GenericXmlApplicationContext ctx;
	private NeuralContext neuralContext;
	
	private JPanel contentPane;
	private NetworkPanel networkPanel;
	private LearnPanel learnPanel;
	private TestPanel testPanel;
	
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		// Spring initialization
		ctx = new GenericXmlApplicationContext();
		ctx.load("classpath:META-INF/spring/application-context.xml");
		ctx.registerShutdownHook();
		ctx.refresh();	
		
		final NeuralNetworkForm frame = ctx.getBean(NeuralNetworkForm.class);
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					//NeuralNetworkForm frame = new NeuralNetworkForm();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public NeuralNetworkForm(NeuralContext context) {
		neuralContext = context;
		setTitle("Neural Network  Trading");

		
		setMinimumSize(new Dimension(800, 600));
		setPreferredSize(new Dimension(1024, 768));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		setContentPane(contentPane);
		SpringLayout sl_contentPane = new SpringLayout();
		contentPane.setLayout(sl_contentPane);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		sl_contentPane.putConstraint(SpringLayout.NORTH, tabbedPane, 0, SpringLayout.NORTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.WEST, tabbedPane, 0, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, tabbedPane, 0, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, tabbedPane, 0, SpringLayout.EAST, contentPane);
		contentPane.add(tabbedPane);

		// Add runtime tab
		Level1RuntimePanel runtimePanel = new Level1RuntimePanel((neuralContext!=null)?neuralContext.getTradingApplicationContext() : null);
		tabbedPane.addTab("Runtime", null, runtimePanel, null);

		
		// Add network tab
		networkPanel = new NetworkPanel(neuralContext);
		networkPanel.updateView();
		networkPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				// Save from ui to context
				networkPanel.updateContext();
			}
			@Override
			public void componentShown(ComponentEvent e) {
				// Load from context to UI
				networkPanel.updateView();
			}
		});
		tabbedPane.addTab("Network", null, networkPanel, null);
		
		// Add learn tab
		learnPanel = new LearnPanel(neuralContext);
		tabbedPane.addTab("Learn", null, learnPanel, null);

		testPanel = new TestPanel(neuralContext);
		tabbedPane.addTab("Test", null, testPanel, null);
		
	}
}
